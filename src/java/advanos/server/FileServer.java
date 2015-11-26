package advanos.server;

import advanos.Protocol;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 *
 * @author Darren
 */
public class FileServer implements Runnable {

    private final ServerSocket server;
    private Path directory;

    public FileServer(ServerSocket server, String port) {
        this.server = server;
        try {
            directory = Paths.get("C:\\CSC611M", port.toString());

            /*Create directory to serve as the file repository of the file server*/
            if (Files.notExists(directory)) {
                Files.createDirectories(directory);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public void run() {
        try {
            while (server.isClosed() == false) {
                try (Socket accept = server.accept();
                        InputStream is = accept.getInputStream();
                        BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                    switch (br.readLine()) {
                        case Protocol.FILE_LIST:
                            try (ObjectOutputStream oos = new ObjectOutputStream(accept.getOutputStream())) {
                                Set<String> list = Files.list(directory)
                                        .map(f -> f.getFileName().toString())
                                        .collect(Collectors.toSet());
                                oos.writeObject(list);
                                oos.flush();
                            }
                            break;
                        case Protocol.UPLOAD:
                            Files.copy(is, directory.resolve(br.readLine()), StandardCopyOption.REPLACE_EXISTING);
                            break;
                        case Protocol.DOWNLOAD:
                            try (OutputStream os = accept.getOutputStream();
                                    InputStream fileSelected = Files.newInputStream(directory.resolve(br.readLine()))) {
                                Protocol.transferBytes(fileSelected, os);
                            }
                    }
                }
            }
        } catch (SocketException ex) {

        } catch (IOException ex) {
            Logger.getLogger(ClientController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    void stop() throws IOException {
        server.close();
    }

}
