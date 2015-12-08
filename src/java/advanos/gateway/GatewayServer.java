package advanos.gateway;

import advanos.Protocol;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
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
    private Set<ServerInfo> servers;

    /**
     * Retrieves file list from known file servers.
     */
    @PostConstruct
    public void init() {
        files = new HashSet<>();

        /*Initialize file servers to be connected*/
        servers = new HashSet<>();
        servers.add(new ServerInfo("localhost", 1099));
        servers.add(new ServerInfo("localhost", 1100));
        servers.add(new ServerInfo("localhost", 1101));
        servers.add(new ServerInfo("localhost", 1102));
        servers.add(new ServerInfo("localhost", 1103));
        servers.add(new ServerInfo("localhost", 1104));

        /*Receive file list from servers*/
        servers.forEach(server -> {
            try {
                getFileList(server);
            } catch (ClassNotFoundException | IOException ex) {
                Logger.getLogger(GatewayServer.class.getName()).log(Level.SEVERE, null, ex);
            }
        });

        /*Sample file upload list*/
        uploadingList = Collections.synchronizedList(new ArrayList());
        uploadingList.add("system.exe");
        uploadingList.add("windows.txt");
    }

    /**
     * Downloads a file given its file name.
     *
     * @param fileName The name of a file to be downloaded
     */
    public void download(String fileName) {
        for (ServerInfo server : servers) {
            try (Socket connection = server.getSocket();
                    OutputStream os = connection.getOutputStream();
                    PrintWriter pw = new PrintWriter(os);
                    InputStream is = connection.getInputStream()) {

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
     * Uploads the selected file to 2/3 of servers.
     */
    public void upload() {
        String filename = file.getSubmittedFileName();
        Integer amount = Protocol.computeReplicationAmount(Protocol.NUMBER_OF_SERVERS);

        // From a list of servers
        Observable.from(servers)
                // Synchronously filter through them by checking if they respond
                .filter(s -> {
                    try {
                        Socket dest = s.getSocket();
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
                    try (Socket dest = fileServer.getSocket();
                    InputStream inputStream = file.getInputStream()) {

                        Protocol.write(dest, Protocol.UPLOAD);

                        Protocol.write(dest, filename);

                        uploadingList.add(filename);
                        Protocol.transferBytes(inputStream, dest.getOutputStream());

                        files.add(filename);
                    } catch (SocketException e) {
                        Logger.getLogger(GatewayServer.class.getName()).log(Level.INFO, "Could not connect to " + fileServer, e);
                    } catch (IOException ex) {
                        Logger.getLogger(GatewayServer.class.getName()).log(Level.SEVERE, null, ex);
                    }

                });
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
        FacesContext fc = FacesContext.getCurrentInstance();
        ExternalContext ec = fc.getExternalContext();
        Map<String, String> parameter = ec.getRequestParameterMap();
        ServerInfo server = new ServerInfo(parameter.get("ip"), Integer.parseInt(parameter.get("port")));
        servers.add(server);
        try {
            getFileList(server);
        } catch (ClassNotFoundException | IOException ex) {
            Logger.getLogger(GatewayServer.class.getName()).log(Level.SEVERE, null, ex);
        }
        return server.toString();
    }

    /**
     * Adds all file names from a server to the file list.
     *
     * @param server the source of file names
     * @throws ClassNotFoundException object does not match with {@code Set<Strings>}
     * @throws IOException connection error with the server
     */
    private void getFileList(ServerInfo server) throws ClassNotFoundException, IOException {
        try (Socket connection = server.getSocket();
                OutputStream out = connection.getOutputStream()) {
            out.write(Protocol.FILE_LIST);
            out.flush();
            try (ObjectInputStream ois = new ObjectInputStream(connection.getInputStream())) {
                Set<String> fileNames = (Set<String>) ois.readObject();
                files.addAll(fileNames);
                System.out.println("Retrieved files from " + server + " are " + fileNames);
            }
        } catch (SocketException e) {
            System.out.println("Cannot get file list from " + server);
        }
    }
}
