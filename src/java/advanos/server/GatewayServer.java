package advanos.server;

import advanos.Protocol;
import advanos.replication.ReplicationService;
import advanos.replication.observers.AliveServersObserver;
import advanos.replication.observers.RetryWithDelay;
import java.io.BufferedReader;
import java.io.DataInputStream;
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
import rx.Observer;
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
