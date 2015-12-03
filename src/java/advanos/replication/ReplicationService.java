package advanos.replication;

import advanos.Protocol;
import advanos.replication.observers.AliveServersObserver;
import advanos.server.FileServerInfo;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import rx.Observable;

public class ReplicationService extends Thread {

    private static ReplicationService instance;
    private final HashMap<Integer, FileServerConnection> connections;
    private final Path directory;

    private ReplicationService() {
        connections = new HashMap<>();
        directory = Paths.get(Protocol.DIRECTORY, "ReplicationService");

        try {
            /*Create directory to serve as the file repository of the file server*/
            if (Files.notExists(directory)) {
                Files.createDirectories(directory);
            }
        } catch (IOException iOException) {

        }
    }

    private Set<FileServerInfo> connectionInfos() {
        return connections.values()
                .stream()
                .map(f -> {
                    return f.getFileServerInfo();
                }).collect(Collectors.toSet());
    }

    @Override
    public void run() {
        initializeConnections();
        while (true) {
            try {
                Thread.sleep(3000);

                Observable<FileServerInfo> aliveFileServerInfos = AliveServersObserver.create(connectionInfos());

                Observable<FileServerConnection> aliveFileServers = aliveFileServerInfos
                        .map(f -> {
                            return connections.get(f.getPort());
                        });

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

        ReplicationService.instance().start();

    }

    private void initializeConnections() {
        for (int port = Protocol.START_PORT; port < Protocol.START_PORT + Protocol.NUMBER_OF_SERVERS; port++) {
            FileServerConnection connection = new FileServerConnection(port);
            connections.put(port, connection);
        }
    }

    private Observable<String> analyzeFileHealth(Observable<FileServerConnection> aliveFileServers) {
        return aliveFileServers
                // For each server connection, query their file lists
                .map(c -> {
                    try {
                        Socket socket = c.getSocket();
                        Protocol.write(socket, Protocol.FILE_LIST);
                        Set<String> readFileList = Protocol.readFileList(socket);
                        socket.close();
                        return readFileList;
                    } catch (IOException ex) {
                        Logger.getLogger(ReplicationService.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    return new HashSet<String>();
                })
                .retry()
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
                    hashmap.forEach((k, v) -> {
                        System.out.println("File " + k + " has " + v + " occurrences");
                    });

                    return hashmap.entrySet();
                })
                // Pair it with the number of alive servers
                .flatMapIterable(entries -> entries)
                // Filter each entry 
                .filter(entry -> {
                    // If the entry has less instances than the number of alive servers, then you need to replicate                    
                    return entry.getValue() < Protocol.computeReplicationAmount(aliveFileServers.count().toBlocking().first());
                })
                .retry()
                .map(f -> {
                    return f.getKey();
                });

        // Filter entry set
    }

    private void replicate(Observable<FileServerConnection> aliveServers, Observable<String> filesThatNeedReplication) {

        // For each of the files that need to be replicated
        filesThatNeedReplication.forEach(filename -> {
            System.out.println("File " + filename + " needs replication");

            aliveServers
                    // Find out which servers have the file
                    .filter(s -> {
                        boolean hasFile = false;
                        try (Socket socket = s.getSocket()) {
                            System.out.println("Finding out who has the file");
                            Protocol.write(socket, Protocol.HAS_FILE);
                            Protocol.write(socket, filename);
                            hasFile = Protocol.readNumber(socket) == Protocol.RESPONSE_HAS_FILE;
                            if (hasFile == false) {
                                System.out.println(s.getFileServerInfo() + " does not have the file");
                            } else {
                                System.out.println(s.getFileServerInfo() + " has the file");
                            }

                        } catch (IOException ex) {
                            Logger.getLogger(ReplicationService.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        return hasFile;
                    })
                    .retry()
                    // Download file once
                    .first(s -> {
                        try (Socket socket = s.getSocket()) {
                            System.out.println("Downloading file once...");
                            Protocol.write(socket, Protocol.DOWNLOAD);
                            System.out.println("Sending file name...");
                            Protocol.write(socket, filename);

                            System.out.println("Reading response...");
                            if (Protocol.readNumber(socket) == Protocol.RESPONSE_HAS_FILE) {
                                System.out.println("Server has the file.");
                                Protocol.receiveFile(socket.getInputStream(), directory, filename);
                                System.out.println("Downloaded file " + filename + " successfully");
                                return true;
                            } else {
                                System.out.println("Server does not have it.");
                                return false;
                            }
                        } catch (IOException ex) {
                            Logger.getLogger(ReplicationService.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        return false;
                    })
                    .retry()
                    // Go back to all the alive servers
                    .flatMap(f -> {
                        return aliveServers;
                    })
                    // determine which alive servers don't have the file
                    .filter(s -> {
                        try (Socket socket = s.getSocket()) {
                            Protocol.write(socket, Protocol.HAS_FILE);
                            Protocol.write(socket, filename);
                            return Protocol.readNumber(socket) != Protocol.RESPONSE_HAS_FILE;
                        } catch (IOException e) {
                            
                        }
                        return false;
                    })
                    .retry()
                    // Determine how many remaining servers are needed to replicate, and take that many
                    .take(Protocol.computeReplicationAmount(Protocol.NUMBER_OF_SERVERS))
                    // send files to those servers
                    .subscribe(s -> {
                        System.out.println("Distributing file " + filename + " to " + s.getFileServerInfo());
                        try (Socket socket = s.getSocket()) {
                            Protocol.write(socket, Protocol.UPLOAD);
                            Protocol.write(socket, filename);
                            Protocol.sendFileBytes(socket, directory.resolve(filename));
                        } catch (IOException ex) {
                            Logger.getLogger(ReplicationService.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    },
                            e -> {
                                System.out.println("Something bad happened.");
                                e.printStackTrace();
                            });

        });

    }

}
