package advanos.server;

import advanos.Protocol;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.ConnectException;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.faces.bean.ApplicationScoped;
import javax.faces.bean.ManagedBean;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.servlet.http.Part;
import rx.Observable;
import rx.schedulers.Schedulers;

/**
 *
 * @author 1
 */
@ManagedBean
@ApplicationScoped
public class GatewayServer implements Serializable {

    private int[] ports;
    private Set<String> files;
    private ExecutorService pool;
    private Part file;
    private FileServer[] servers;

    @PostConstruct
    public void init() {
        ports = new int[Protocol.NUMBER_OF_SERVERS];
        files = new HashSet<>();
        pool = Executors.newFixedThreadPool(Protocol.NUMBER_OF_SERVERS);
        servers = new FileServer[Protocol.NUMBER_OF_SERVERS];

        /*Start all servers*/
        for (int i = 0; i < Protocol.NUMBER_OF_SERVERS; i++) {
            ports[i] = Protocol.START_PORT + i;
            try {
                servers[i] = new FileServer(ports[i]);
                pool.execute(servers[i]);
            } catch (IOException ex) {
                Logger.getLogger(GatewayServer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        /*Receive file list from servers*/
        for (int i = 0; i < Protocol.NUMBER_OF_SERVERS; i++) {
            try (Socket connection = new Socket("localhost", ports[i]);
                    OutputStream out = connection.getOutputStream()) {
                Protocol.write(connection, Protocol.FILE_LIST);
                Set<String> fileList = Protocol.readFileList(connection);
                files.addAll(fileList);
            } catch (IOException ex) {
                Logger.getLogger(GatewayServer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * Close all file servers and shutdown thread pool.
     */
    @PreDestroy
    public void cleanup() {
        Arrays.stream(servers).forEach(server -> {
            try {
                server.stop();
            } catch (IOException ex) {
                Logger.getLogger(GatewayServer.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        pool.shutdown();
    }

    public void download(String fileName) {
        for (int port : ports) {
            try (Socket server = new Socket("localhost", port);
                    OutputStream os = server.getOutputStream();
                    InputStream is = server.getInputStream();
                    DataOutputStream dos = new DataOutputStream(os);
                    DataInputStream dis = new DataInputStream(is)) {

                /*Protocol*/
                dos.writeInt(Protocol.DOWNLOAD);
                dos.writeUTF(fileName);
                dos.flush();

                /*Check if file exists, 1 if present, 0 if otherwise*/
                int reply = dis.readInt();
                if (reply == 1) {

                    /*Response header*/
                    FacesContext fc = FacesContext.getCurrentInstance();
                    ExternalContext ec = fc.getExternalContext();
                    ec.responseReset();
                    ec.setResponseContentType(ec.getMimeType(fileName));
                    ec.setResponseHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

                    /*Write bytes*/
                    Protocol.transferBytes(is, ec.getResponseOutputStream());
                    fc.responseComplete();
                    break;
                }
            } catch (IOException ex) {
                Logger.getLogger(GatewayServer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * Uploads the selected file to 2/3 of servers.
     */
    public void upload() {
        String filename = file.getSubmittedFileName();

        Set<FileServerInfo> infos = Arrays
                .stream(servers)
                .map(f -> {
                    return f.getInformation();
                })
                .collect(Collectors.toSet());

        Integer amount = Protocol.computeReplicationAmount(Protocol.NUMBER_OF_SERVERS); //AliveServersObserver.create(infos).count().toBlocking().first());

        // From a list of servers
        Observable.from(servers)
                // Synchronously filter through them by checking if they respond
                .filter(s -> {
                    try {
                        Socket dest = s.connect();
                        Protocol.ping(dest);
                        int response = Protocol.readNumber(dest);
                        return response == Protocol.RESPONSE_PING_ALIVE;
                    } catch (ConnectException e) {
                        Logger.getLogger(GatewayServer.class.getName()).log(Level.INFO, "Could not connect to {0}", s);
                    } catch (IOException ex) {
                        Logger.getLogger(GatewayServer.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    return false;
                })
                // Get only the amount
                .take(amount)
                .subscribeOn(Schedulers.newThread())
                // And that number should update
                .subscribe(fileServer -> {
                    try (Socket dest = fileServer.connect();
                            InputStream inputStream = file.getInputStream()) {

                        Protocol.write(dest, Protocol.UPLOAD);

                        Protocol.write(dest, filename);

                        long filesize = file.getSize();
                        System.out.println("meta data size: " + filesize);
                        Protocol.write(dest, filesize);

                        Protocol.transferBytes(inputStream, dest.getOutputStream());

                        files.add(filename);
                        System.out.println("File " + filename + " added.");
                    } catch (ConnectException e) {
                        Logger.getLogger(GatewayServer.class.getName()).log(Level.INFO, "Could not connect to {0}", fileServer);
                    } catch (IOException ex) {
                        Logger.getLogger(GatewayServer.class.getName()).log(Level.SEVERE, null, ex);
                    }

                });
    }

    public Part getFile() {
        return file;
    }

    public Set<String> getFiles() {
        return files;
    }

    public FileServer[] getServers() {
        return servers;
    }

    public void setFile(Part file) {
        this.file = file;
    }

    public void killServer(int port) {
        Arrays.stream(servers)
                .filter(s -> s.getPort() == port)
                .collect(Collectors.toSet())
                .forEach(s -> {
                    try {
                        s.stop();

                    } catch (IOException ex) {
                        Logger.getLogger(GatewayServer.class
                                .getName()).log(Level.SEVERE, null, ex);
                    }
                });
    }

    public void startServer(int port) {
        Arrays.stream(servers)
                .filter(s -> s.getPort() == port)
                .collect(Collectors.toSet())
                .forEach(s -> {
                    try {
                        s.start();

                    } catch (IOException ex) {
                        Logger.getLogger(GatewayServer.class
                                .getName()).log(Level.SEVERE, null, ex);
                    }
                });
    }

}
