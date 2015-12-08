package advanos.gateway;

import advanos.Protocol;
import advanos.replication.ReplicationService;
import advanos.replication.observers.AliveServersObserver;
import advanos.replication.observers.RetryWithDelay;
import advanos.server.FileServerInfo;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.faces.bean.ApplicationScoped;
import javax.faces.bean.ManagedBean;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.servlet.http.Part;
import rx.Observable;
import rx.schedulers.Schedulers;

/**
 * Controller for file upload and download.
 *
 * @author CSC611M G01
 */
@ManagedBean
@ApplicationScoped
public class GatewayServer implements Serializable {

    private Set<String> files;
    private List<String> uploadingList;
    private Part file;
    private Set<FileServerInfo> servers;

    /**
     * Retrieves file list from known file servers.
     */
    @PostConstruct
    public void init() {
        files = Collections.synchronizedSet(new HashSet<>());

        /*Initialize file servers to be connected*/
        servers = new HashSet<>();
        servers.add(new FileServerInfo("localhost", 1099));
        servers.add(new FileServerInfo("localhost", 1100));
        servers.add(new FileServerInfo("localhost", 1101));
        servers.add(new FileServerInfo("localhost", 1102));
        servers.add(new FileServerInfo("localhost", 1103));
        servers.add(new FileServerInfo("localhost", 1104));

        servers.forEach(s -> registerServer(s));

        /*Sample file upload list*/
        uploadingList = Collections.synchronizedList(new ArrayList());
        uploadingList.add("system.exe");
        uploadingList.add("windows.txt");
    }

    public void download(String filename) {

        Observable<FileServerInfo> aliveServers = AliveServersObserver.create(servers);
        final FacesContext fc = FacesContext.getCurrentInstance();
        final ExternalContext ec = fc.getExternalContext();
        aliveServers
                .filter(f -> {
                    try (Socket connect = f.getSocket()) {
                        Protocol.ping(connect);
                        Integer readNumber = Protocol.readNumber(connect);
                        return readNumber == Protocol.RESPONSE_PING_ALIVE;
                    } catch (IOException ex) {
                        Logger.getLogger(GatewayServer.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    return false;
                })
                .retryWhen(new RetryWithDelay(3, 2000))
                .firstOrDefault(null,
                        f -> {
                            try (Socket connect = f.getSocket()) {
                                Protocol.write(connect, Protocol.HAS_FILE);
                                Protocol.write(connect, filename);
                                return Protocol.readNumber(connect) == Protocol.RESPONSE_HAS_FILE;
                            } catch (IOException ex) {

                            }
                            return false;
                        })
                .map(
                        s -> {
                            if (s == null) {
                                throw new NullPointerException("There are no available servers.");
                            }
                            try (Socket socket = s.getSocket()) {
                                System.out.println("Downloading file once from " + s);
                                Protocol.write(socket, Protocol.DOWNLOAD);
                                System.out.println("Sending file name...");
                                Protocol.write(socket, filename);

                                System.out.println("Reading response...");
                                if (Protocol.readNumber(socket) == Protocol.RESPONSE_HAS_FILE) {
                                    System.out.println("Server has the file.");

                                    DataInputStream dis = new DataInputStream(socket.getInputStream());
                                    long size = dis.readLong();

                                    System.out.println("file size: " + size);
                                    /*Response header*/

                                    try {

                                        ec.responseReset();
                                        ec.setResponseContentType(ec.getMimeType(filename));
                                        ec.setResponseHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
                                        System.out.println("Headers written");
                                        /*Write bytes*/
                                        Protocol.transferBytes(dis, ec.getResponseOutputStream());
                                        fc.responseComplete();
                                    } catch (IllegalStateException e) {

                                    }

                                    System.out.println("Downloaded file " + filename + " successfully");
                                } else {
                                    throw new NullPointerException("The server does not have the file.");
                                }
                            } catch (IOException ex) {
                                Logger.getLogger(GatewayServer.class.getName()).log(Level.SEVERE, null, ex);
                            }
                            return s;
                        })
                .subscribeOn(Schedulers.newThread())
                .subscribe(
                        s -> {
                            if (s == null) {
                                System.out.println("All our servers are down as of the moment. Sorry!");
                            } else {
                                System.out.println("Successfully downloaded the file");
                            }
                        },
                        e -> {
                            System.out.println("Something bad happened during download. " + e.getMessage());
                            e.printStackTrace();
                        });

    }

    /**
     * Uploads the selected file to 2/3 of servers.
     */
    public void upload() {
        String filename = file.getSubmittedFileName();

        // From a list of servers
        Observable.from(servers)
                // Synchronously filter through them by checking if they respond
                .filter(s -> {
                    try {
                        Socket dest = s.getSocket();
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
                .take(1)
                .subscribeOn(Schedulers.newThread())
                // And that number should update

                .subscribe(fileServer -> {
                    try (Socket dest = fileServer.getSocket();
                    InputStream inputStream = file.getInputStream()) {

                        Protocol.write(dest, Protocol.UPLOAD);

                        Protocol.write(dest, filename);

                        long filesize = file.getSize();
                        System.out.println("meta data size: " + filesize);
                        Protocol.write(dest, filesize);

                        uploadingList.add(filename);
                        Protocol.transferBytes(inputStream, dest.getOutputStream());

                        files.add(filename);
                        System.out.println("File " + filename + " added.");
                    } catch (ConnectException e) {

                    } catch (IOException ex) {
                        Logger.getLogger(GatewayServer.class.getName()).log(Level.SEVERE, null, ex);
                    }

                });
    }

    /**
     * Retrieves a list of file currently uploading. It is used by the
     * replication service
     */
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
     * Gets the file which is selected for upload. Used in the web page.
     *
     * @return
     */
    public Part getFile() {
        return file;
    }

    /**
     * Gets all files that can be downloaded.
     *
     * @return All files that can be downloaded
     */
    public Set<String> getFiles() {
        return files;
    }

    /**
     * Sets the file to be uploaded. Used in the web page.
     *
     * @param file
     */
    public void setFile(Part file) {
        this.file = file;
    }

    /**
     * Notifies this gate way that a file has already been uploaded. It removes
     * the specified file on the request parameter from the upload file list.
     */
    public void uploadingFinished() {
        FacesContext fc = FacesContext.getCurrentInstance();
        ExternalContext ec = fc.getExternalContext();
        Map<String, String> request = ec.getRequestParameterMap();
        String name = request.get("file");
        uploadingList.remove(name);
    }

    /**
     * Registers the file server in this gateway given parameters ip and port
     *
     * @return IP address and port of the file server
     */
    public String registerServer() {
        System.out.println("Registering server");
        FacesContext fc = FacesContext.getCurrentInstance();
        ExternalContext ec = fc.getExternalContext();
        Map<String, String> parameter = ec.getRequestParameterMap();
        FileServerInfo server = new FileServerInfo(parameter.get("ip"), Integer.parseInt(parameter.get("port")));
        servers.add(server);
        new Thread(() -> {
            registerServer(server);
        }).start();

        return server.toString();
    }

    private void registerServer(FileServerInfo server) {
        /*Retrieve file list from server*/

        System.out.println("Gateway found a server: " + server);
        try (Socket connection = server.getSocket()) {
            Protocol.write(connection, Protocol.FILE_LIST);
            Set<String> fileNames = Protocol.readFileList(connection);
            files.addAll(fileNames);
            System.out.println("Retrieved files from " + server + " are " + fileNames);
        } catch (SocketException e) {
            System.out.println("Cannot get file list from " + server);
        } catch (IOException ex) {
            Logger.getLogger(GatewayServer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
