<<<<<<< HEAD
package advanos.server;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class FileServer implements Runnable {

    private FileServerInfo information;
    private ServerSocket server;
    private final int port;
    private final Path directory;
    private final ExecutorService pool;
    
    

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

        pool = Executors.newFixedThreadPool(2);
        
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

        if (true) {
            System.out.println("Server " + port + " was turned on");
        }

        try {
            while (true) {
                Socket dest = server.accept();
                pool.execute(new FileServerHandleConnection(dest, information, getFileList()));
                
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
=======
package advanos.server;

import advanos.gateway.GatewayServer;
import advanos.Protocol;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
    }

    public boolean isClosed() {
        return server.isClosed();
    }

    @Override
    public void run() {
        try {

            /*Create a new server socket for this file server*/
            server = new ServerSocket(port);
            String ipAddress = InetAddress.getLocalHost().getHostAddress();
            String localPort = Integer.toString(server.getLocalPort());
            String addressPort = ipAddress + ":" + localPort;
            System.out.println("File server " + addressPort + " started.");

            /*Inform the gateway that this server is alive*/
            URL gateway = new URL("http://localhost:8080/advanos-sync-project-yehey/faces/register.xhtml?ip=" + ipAddress + "&port=" + localPort);
            try (InputStream is = gateway.openStream()) {
                System.out.println("File server " + addressPort + " connected to the gateway.");
            } catch (FileNotFoundException e) {
                System.out.println("File server " + addressPort + " cannot connect to the gateway.");
            }

            /*File server operations*/
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
                            try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
                                String fileName = bufferedReader.readLine();
                                long size = Files.copy(inputStream, directory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
                                System.out.println(fileName + " " + size + " was uploaded to " + addressPort);

                                /*Establish a URL connection to the gateway to notify that the file was uploded*/
                                gateway = new URL("http://localhost:8080/advanos-sync-project-yehey/faces/uploadingfinish.xhtml?file=" + fileName);
                                try (InputStream connect = gateway.openStream()) {
                                    System.out.println(server + "Informed gateway about uploaded file.");
                                } catch (FileNotFoundException e) {
                                    System.out.println("Gateway is offline.");
                                }
                            }
                            break;
                        case Protocol.DOWNLOAD:
                            Protocol.sendRequestedFile(dest, information);
                            System.out.println(" was downloaded from " + addressPort);
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
                                Protocol.write(dest, Protocol.RESPONSE_HAS_FILE);
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
            Logger.getLogger(FileServer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Stops this file server.
     *
     * @throws IOException If there are problems on closing
     */
    public void stop() throws IOException {
        server.close();
        System.out.println("File server " + InetAddress.getLocalHost().getHostAddress() + ":" + port + " was closed");
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

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return "File Server " + port;
    }
}
>>>>>>> File-index
