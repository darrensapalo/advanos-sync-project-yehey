package advanos.replication;

import advanos.Protocol;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import rx.Observable;

public class ReplicationService extends Thread {

    private static ReplicationService instance;
    private final HashMap<Integer, FileServerConnection> connections;
    private final Path directory;

    private ReplicationService() {
        connections = new HashMap<>();
        fileIndex = new HashMap<>();
        directory = Paths.get(Protocol.DIRECTORY, "ReplicationService");
    }

    @Override
    public void run() {
        initializeConnections();
        while (true) {
            try {
                Thread.sleep(10000);

                Observable<FileServerConnection> aliveFileServers = pingConnections();

                Observable<String> filesThatNeedReplication = analyzeFileHealth(aliveFileServers);

                replicate(aliveFileServers, filesThatNeedReplication);

            } catch (InterruptedException ex) {
                Logger.getLogger(ReplicationService.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private static ReplicationService instance() {
        if (instance == null) {
            instance = new ReplicationService();
        }
        return instance;
    }

    public static void main(String[] args) {

        ReplicationService.instance();

        try (Socket server = new Socket("localhost", 1099)) {
            System.out.println("ggg");
        } catch (ConnectException e) {
            System.out.println("Failed");
            System.out.println(e + " first");
            try (Socket server2 = new Socket("localhost", 1100);
                    OutputStream os = server2.getOutputStream();
                    InputStream is = server2.getInputStream();
                    ObjectInputStream ois = new ObjectInputStream(is)) {
                System.out.println("then");
                os.write(Protocol.COPY_ALL);
                os.flush();
                Set<String> fileNames = (Set<String>) ois.readObject();
                try (Socket server3 = new Socket("localhost", 1101);
                        OutputStream os3 = server3.getOutputStream();
                        ObjectOutputStream oos = new ObjectOutputStream(os3)) {
                    os.write(Protocol.PASTE_ALL);
                    oos.writeObject(oos);
                    oos.flush();
                    for (String file : fileNames) {
                        byte[] buffer = new byte[1024];
                        while (is.read(buffer) > -1) {
                            os3.write(buffer);
                        }
                        os3.flush();
                    }
                }
            } catch (ClassNotFoundException | IOException ex) {
                System.out.println(ex + " second");
                e.printStackTrace();
            }
        } catch (IOException e) {
            System.out.println(e);
        }
    }

    private void initializeConnections() {
        for (int port = Protocol.START_PORT; port < Protocol.START_PORT + Protocol.NUMBER_OF_SERVERS; port++) {
            FileServerConnection connection = new FileServerConnection(port);
            connections.put(port, connection);
        }
    }

    private Observable<FileServerConnection> pingConnections() {
        return Observable.from(connections.values())
                // Get all the alive servers
                .filter(c -> {
                    Socket socket = c.getSocket();
                    Protocol.ping(socket);
                    return Protocol.readNumber(socket) == Protocol.RESPONSE_PING_ALIVE;
                });
    }

    private Observable<String> analyzeFileHealth(Observable<FileServerConnection> aliveFileServers) {
        return aliveFileServers
                // For each server connection, query their file lists
                .map(c -> {
                    Socket socket = c.getSocket();
                    Protocol.write(socket, Protocol.FILE_LIST);
                    return Protocol.readFileList(socket);
                })
                // Accumulate all of these file lists into a hashmap that counts all the references of each file
                .reduce(new HashMap<String, Integer>(), (hashmap, fileList) -> {

                    fileList.forEach(filename -> {
                        Integer count = hashmap.get(filename);
                        if (count == null || count == 0) {
                            hashmap.put(filename, 1);
                        } else {
                            hashmap.put(filename, count + 1);
                        }
                    });

                    return hashmap;

                })
                // View as list
                .map(hashmap -> {
                    return hashmap.entrySet();
                })
                // Pair it with the number of alive servers
                .flatMapIterable(entries -> entries)
                // Filter each entry 
                .filter(entry -> {
                    // If the entry has less instances than the number of alive servers, then you need to replicate                    
                    return entry.getValue() < Protocol.computeReplicationAmount(aliveFileServers.count().toBlocking().first());
                })
                .map(f -> {
                    return f.getKey();
                });

        // Filter entry set
    }

    private void replicate(Observable<FileServerConnection> aliveServers, Observable<String> filesThatNeedReplication) {

        // For each of the files that need to be replicated
        filesThatNeedReplication.forEach(filename -> {

            aliveServers
                    // Find out which servers have the file
                    .filter(s -> {
                        Socket socket = s.getSocket();
                        Protocol.write(socket, Protocol.HAS_FILE);
                        Protocol.write(socket, filename);
                        return Protocol.readNumber(socket) == Protocol.RESPONSE_HAS_FILE;
                    })
                    // Download file once
                    .first(s -> {
                        try {
                            Socket socket = s.getSocket();
                            Protocol.write(socket, Protocol.DOWNLOAD);
                            Protocol.write(socket, filename);
                            if (Protocol.readNumber(socket) == Protocol.RESPONSE_HAS_FILE) {
                                Protocol.readFile(socket.getInputStream(), directory);
                                return true;
                            }
                        } catch (IOException ex) {
                            Logger.getLogger(ReplicationService.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        return false;
                    })
                    // Go back to all the alive servers
                    .flatMap(f -> {
                        return aliveServers;
                    })
                    // determine which alive servers don't have the file
                    .filter(s -> {
                        Socket socket = s.getSocket();
                        Protocol.write(socket, Protocol.HAS_FILE);
                        Protocol.write(socket, filename);
                        return Protocol.readNumber(socket) != Protocol.RESPONSE_HAS_FILE;
                    })
                    // Determine how many remaining servers are needed to replicate, and take that many
                    .take(aliveServers.count().toBlocking().first())
                    // send files to those servers
                    .map(s -> {
                        Socket socket = s.getSocket();
                        Protocol.write(socket, Protocol.UPLOAD);
                        Protocol.write(socket, filename);
                        Protocol.sendFileBytes(socket, directory.resolve(filename));
                        return s;
                    });

        });

        
    }

}
