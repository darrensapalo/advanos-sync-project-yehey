package advanos.server;

import advanos.Protocol;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.faces.bean.ApplicationScoped;
import javax.faces.bean.ManagedBean;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.servlet.http.Part;

/**
 *
 * @author 1
 */
@ManagedBean
@ApplicationScoped
public class ClientController implements Serializable {

    private Map<Integer, FileServer> servers;
    private Map<String, Set<Integer>> files;
    private ExecutorService pool;
    private Part file;

    /**
     * Number of servers
     */
    private static final int NUM_SERV = 6;

    @PostConstruct
    public void init() {
        files = new HashMap<>();
        servers = new HashMap<>();
        pool = Executors.newFixedThreadPool(NUM_SERV);

        /*Start all servers*/
        for (int i = 0; i < NUM_SERV; i++) {
            Integer port = 1099 + i;
            createServer(port);

            /*Gateway: Receive file list from server*/
            try (Socket connection = new Socket("localhost", port);
                    PrintWriter pw = new PrintWriter(connection.getOutputStream())) {
                pw.println(Protocol.FILE_LIST);
                pw.flush();
                try (ObjectInputStream ois = new ObjectInputStream(connection.getInputStream())) {
                    Set<String> fileNames = (Set<String>) ois.readObject();
                    fileNames.stream()
                            .forEach(f -> addFile(f, port));
                }
            } catch (ClassNotFoundException | IOException ex) {
                Logger.getLogger(ClientController.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * Close all file servers and shutdown thread pool.
     */
    @PreDestroy
    public void cleanup() {
        servers.values().forEach((fileServer) -> {
            try {
                fileServer.stop();
            } catch (IOException ex) {
                Logger.getLogger(ClientController.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        pool.shutdown();
    }

    public void download(String fileName) {
        /*Replicate file with other servers if necessary*/
        /*Response*/
        FacesContext fc = FacesContext.getCurrentInstance();
        ExternalContext ec = fc.getExternalContext();
        ec.responseReset();
        ec.setResponseContentType(ec.getMimeType(fileName));
        ec.setResponseHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

        /*Perform byte transfer*/
        try (Socket server = new Socket("localhost", files.get(fileName).stream().findAny().get());
                PrintWriter pw = new PrintWriter(server.getOutputStream());
                InputStream is = server.getInputStream()) {
            pw.println(Protocol.DOWNLOAD);
            pw.println(fileName);
            pw.flush();
            Protocol.transferBytes(is, ec.getResponseOutputStream());
            fc.responseComplete();
        } catch (IOException ex) {
            Logger.getLogger(ClientController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public Part getFile() {
        return file;
    }

    public Set<String> getFiles() {
        return files.keySet();
    }

    public Map<Integer, FileServer> getServers() {
        return servers;
    }

    public void setFile(Part file) {
        this.file = file;
    }

    public void createServer(Integer port) {
        try {

            ServerSocket server = new ServerSocket(port);
            FileServer fileServer = new FileServer(server, Integer.toString(port));
            
            /*Spawn threads to start file servers*/
            pool.execute(fileServer);
            
            servers.put(port, fileServer);
        } catch (IOException ex) {
            Logger.getLogger(ClientController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void stop(Integer port) {
        try {
            servers.get(port).stop();
        } catch (IOException ex) {
            Logger.getLogger(ClientController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Uploads the selected file to 2/3 of servers.
     */
    public void upload() {
        int limit = Math.floorDiv(NUM_SERV * 2, 3);

        /*First 2/3 servers seen*/
        for (Integer port : servers.keySet()) {

            /*Update the list*/
            String filename = file.getSubmittedFileName();
            addFile(filename, port);

            /*Connect to server*/
            try (OutputStream os = new Socket("localhost", port).getOutputStream();
                    PrintWriter pw = new PrintWriter(os);
                    InputStream is = file.getInputStream()) {
                pw.println(Protocol.UPLOAD);
                pw.println(filename);
                pw.flush();
                Protocol.transferBytes(is, os);
            } catch (IOException ex) {
                Logger.getLogger(ClientController.class.getName()).log(Level.SEVERE, null, ex);
            }

            /*Exit*/
            if (--limit <= 0) {
                break;
            }
        }
    }

    private void addFile(String file, Integer port) {
        if (files.containsKey(file)) {
            files.get(file).add(port);
        } else {
            files.put(file, new HashSet<>());
        }
    }
}
