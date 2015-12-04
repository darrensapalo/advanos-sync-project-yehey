package advanos.server;

import advanos.Protocol;
import advanos.replication.observers.AliveServersObserver;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.faces.bean.ApplicationScoped;
import javax.faces.bean.ManagedBean;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.servlet.http.Part;
import rx.Observable;

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
    private List<String> uploadingList;

    ;

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
                out.write(Protocol.FILE_LIST);
                out.flush();
                try (ObjectInputStream ois = new ObjectInputStream(connection.getInputStream())) {
                    Set<String> fileNames = (Set<String>) ois.readObject();
                    files.addAll(fileNames);
                }
            } catch (ClassNotFoundException | IOException ex) {
                Logger.getLogger(GatewayServer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        uploadingList = Collections.synchronizedList(new ArrayList());
        uploadingList.add("system.exe");
        uploadingList.add("windows.txt");
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
                    PrintWriter pw = new PrintWriter(os);
                    InputStream is = server.getInputStream()) {

                /*Protocol*/
                os.write(Protocol.DOWNLOAD);
                pw.println(fileName);
                pw.flush();

                /*Check if file exists, 1 if present, 0 if otherwise*/
                int reply = is.read();
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

    public void getUploadingList() {
        FacesContext fc = FacesContext.getCurrentInstance();
        ExternalContext ec = fc.getExternalContext();
        ec.responseReset();
        try {
            PrintWriter pw = new PrintWriter(ec.getResponseOutputWriter());
            uploadingList.forEach(pw::println);
        } catch (IOException ex) {
            Logger.getLogger(GatewayServer.class.getName()).log(Level.SEVERE, null, ex);
        }
        fc.responseComplete();
    }

    /**
     * Uploads the selected file to 2/3 of servers.
     */
    public void upload() {
        String filename = file.getSubmittedFileName();
        uploadingList.add(filename);

        Set<FileServerInfo> infos = Arrays
                .stream(servers)
                .map(f -> {
                    return f.getInformation();
                })
                .collect(Collectors.toSet());

        Integer amount = Protocol.computeReplicationAmount(AliveServersObserver.create(infos).count().toBlocking().first());

        // From a list of servers
        Observable.from(servers)
                // Synchronously filter through them by checking if they respond
                .filter(s -> {
                    try {
                        Socket dest = s.connect();
                        Protocol.ping(dest);
                        int response = Protocol.readNumber(dest);
                        return response == Protocol.RESPONSE_PING_ALIVE;
                    } catch (SocketException e) {
                        Logger.getLogger(GatewayServer.class.getName()).log(Level.INFO, "Could not connect to " + s, e);
                    } catch (IOException ex) {
                        Logger.getLogger(GatewayServer.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    return false;
                })
                // Get only the amount
                .take(amount)
                // And that number should update
                .subscribe(fileServer -> {
                    try (Socket dest = fileServer.connect();
                    InputStream inputStream = file.getInputStream()) {

                        Protocol.write(dest, Protocol.UPLOAD);

                        Protocol.write(dest, filename);

                        Protocol.transferBytes(inputStream, dest.getOutputStream());

                        files.add(filename);
                    } catch (SocketException e) {
                        Logger.getLogger(GatewayServer.class.getName()).log(Level.INFO, "Could not connect to " + fileServer, e);
                    } catch (IOException ex) {
                        Logger.getLogger(GatewayServer.class.getName()).log(Level.SEVERE, null, ex);
                    }

                });
        uploadingList.remove(filename);
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

    public void uploadingFinished() {
        FacesContext fc = FacesContext.getCurrentInstance();
        ExternalContext ec = fc.getExternalContext();
        Map<String, String> request = ec.getRequestParameterMap();
        String name = request.get("file");
        uploadingList.remove(name);
    }
    public void registerServer() {
        
        FacesContext fc = FacesContext.getCurrentInstance();
        ExternalContext ec = fc.getExternalContext();
        ec.responseReset();
        try {
            PrintWriter pw = new PrintWriter(ec.getResponseOutputWriter());
            pw.println(ec.getRequestServerName());
            pw.println(ec.getRequestParameterMap());
        } catch (IOException ex) {
            Logger.getLogger(GatewayServer.class.getName()).log(Level.SEVERE, null, ex);
        }
        fc.responseComplete();
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
