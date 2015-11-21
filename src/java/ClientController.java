
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.SessionScoped;
import javax.servlet.http.Part;

/**
 *
 * @author 1
 */
@ManagedBean
@SessionScoped
public class ClientController {

    private Map<Integer, ServerSocket> servers;
    private Map<String, Set<Integer>> files;
    private Part file;
    private static final String DOWNLOAD = "download";
    private static final String FILE_LIST = "file list";
    private static final String UPLOAD = "upload";

    /**
     * Number of servers
     */
    private static final int NUM_SERV = 6;

    @PostConstruct
    public void init() {
        files = new HashMap<>();
        servers = new LinkedHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(NUM_SERV);

        /*Start all servers*/
        for (int i = 0; i < NUM_SERV; i++) {
            Integer port = 1099 + i;
            try {

                /*Send file list from server to gateway*/
                Path directory = Paths.get("C:\\CSC611M", port.toString());
                if (Files.notExists(directory)) {
                    Files.createDirectories(directory);
                }
                ServerSocket server = new ServerSocket(port);
                servers.put(port, server);

                /*Spawn threads from incoming data from gateway to server*/
                pool.execute(() -> {
                    while (true) {
                        try (Socket accept = server.accept();
                                InputStream is = accept.getInputStream();
                                BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                            switch (br.readLine()) {
                                case FILE_LIST:
                                    try (ObjectOutputStream oos = new ObjectOutputStream(accept.getOutputStream())) {
                                        Set<String> files = Files.list(directory)
                                                .map(file -> file.getFileName().toString())
                                                .collect(Collectors.toSet());
                                        oos.writeObject(files);
                                        oos.flush();
                                    }
                                    break;
                                case UPLOAD:
                                    String fileName = br.readLine();
                                    System.out.println(directory.resolve(fileName));
                                    Files.copy(is, directory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
                                    break;
                                case DOWNLOAD:
                                    try (OutputStream os = accept.getOutputStream();
                                            InputStream fileSelected = Files.newInputStream(directory.resolve(br.readLine()))) {
                                        transferBytes(fileSelected, os);
                                    }
                            }
                        } catch (IOException ex) {
                            Logger.getLogger(ClientController.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                });

                /*Gateway: Receive file list from server*/
                try (Socket connection = new Socket("localhost", port);
                        PrintWriter pw = new PrintWriter(connection.getOutputStream())) {
                    pw.println(FILE_LIST);
                    pw.flush();
                    try (ObjectInputStream ois = new ObjectInputStream(connection.getInputStream())) {
                        Set<String> fileNames = (Set<String>) ois.readObject();
                        fileNames.stream()
                                .forEach(file -> addFile(file, port));
                    }
                }
            } catch (ClassNotFoundException | IOException ex) {
                Logger.getLogger(ClientController.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public Part getFile() {
        return file;
    }

    public Set<String> getFiles() {
        return files.keySet();
    }

    public void setFile(Part file) {
        this.file = file;
    }

    /**
     * Uploads the selected file to 2/3 of servers.
     */
    public void upload() {
        int limit = Math.floorDiv(NUM_SERV * 2, 3);

        /*First 2/3 servers seen*/
        for (Integer port : servers.keySet()) {

            /*Update the list*/
            String fileName = file.getSubmittedFileName();
            addFile(fileName, port);

            /*Connect to server*/
            try (OutputStream os = new Socket("localhost", port).getOutputStream();
                    PrintWriter pw = new PrintWriter(os);
                    InputStream is = file.getInputStream()) {
                pw.println(UPLOAD);
                pw.println(fileName);
                pw.flush();
                transferBytes(is, os);
            } catch (IOException ex) {
                Logger.getLogger(ClientController.class.getName()).log(Level.SEVERE, null, ex);
            }

            /*Exit*/
            if (--limit <= 0) {
                break;
            }
        }
    }

    private void transferBytes(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[1024];
        while (is.read(buffer) > -1) {
            os.write(buffer);
        }
        os.flush();
    }

    private void addFile(String file, Integer port) {
        if (files.containsKey(file)) {
            files.get(file).add(port);
        } else {
            files.put(file, new HashSet<>());
        }
    }
}
