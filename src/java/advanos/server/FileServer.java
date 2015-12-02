package advanos.server;

import advanos.Protocol;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class FileServer implements Runnable {

    private FileServerInfo information;
    private ServerSocket server;
    private final int port;
    private final Path directory;

    public FileServer(int port) throws IOException {
        this.port = port;
        directory = Paths.get("C:\\CSC611M", Integer.toString(port));

        /*Create directory to serve as the file repository of the file server*/
        if (Files.notExists(directory)) {
            Files.createDirectories(directory);
        }

        information = new FileServerInfo();
        information.setPort(port);
        information.setFileList(getFileList());

        start();
    }

    public boolean isClosed() {
        return server.isClosed();
    }

    public void start() throws IOException {
        server = new ServerSocket(port);
    }

    public void stop() throws IOException {
        server.close();
    }

    @Override
    public void run() {

        try {
            while (true) {
                try (Socket dest = server.accept();
                        InputStream inputStream = dest.getInputStream()) {
                    switch (inputStream.read()) {
                        case Protocol.SERVER_INFO:
                            Protocol.sendObject(dest, information);
                            break;

                        case Protocol.FILE_LIST:
                            Protocol.sendObject(dest, getFileList());
                            break;

                        case Protocol.UPLOAD:
                            Protocol.readFile(inputStream, information);

                            break;
                        case Protocol.DOWNLOAD:
                            Protocol.sendRequestedFile(dest, information);
                            break;

                        /*Sends all files of this server starting with a set of file names*/
                        case Protocol.COPY_ALL:
                            Protocol.copyAll(dest, information);
                            break;

                        /*Receives all files of this server starting with a set of file names*/
                        case Protocol.PASTE_ALL:
                            Protocol.pasteAll(dest, information);
                            break;

                        /* Responds with 0 if there are no issues */
                        case Protocol.PING:
                            Protocol.write(dest, 0);
                            break;

                        case Protocol.HAS_FILE:
                            String fileName = Protocol.readLine(dest);
                            if (getFileList().contains(fileName)) {
                                Protocol.write(dest, 1);
                            } else {
                                Protocol.write(dest, 0);
                            }
                            break;
                    }
                }
            }
        } catch (SocketException ex) {
            //Socket is closed
            System.out.println(ex + " " + port);
        } catch (IOException ex) {
            Logger.getLogger(GatewayServer.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    /**
     *
     * @param oos Given an ObjectOutputStream, sends the list of files as a
     * Set<String>
     * @return the list of files
     * @throws IOException
     */
    private Set<String> sendFileList(ObjectOutputStream oos) throws IOException {
        Set<String> list = getFileList();
        oos.writeObject(list);
        oos.flush();
        return list;
    }

    /**
     *
     * @return a set of String objects, the list of files in this server
     * @throws IOException
     */
    public Set<String> getFileList() throws IOException {
        return Files.list(directory)
                .map(f -> f.getFileName().toString())
                .collect(Collectors.toSet());
    }

    public FileServerInfo getInformation() {
        return information;
    }

    public Socket connect() throws IOException {
        System.out.println("Creating a new connection to file server " + port);
        return connect(information.getPort());
    }

    public static Socket connect(int port) throws IOException {
        return new Socket("localhost", port);
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return "File Server " + port;
    }
}
