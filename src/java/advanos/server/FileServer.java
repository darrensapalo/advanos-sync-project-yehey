package advanos.server;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
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

    private ServerSocket server;
    private final int port;
    private final Path directory;
    private final FileServerInfo information;
    private final ExecutorService pool;

    public FileServer(int port) throws IOException {
        this.port = port;
        this.information = new FileServerInfo(InetAddress.getLocalHost().getHostAddress(), port);
        directory = Paths.get("C:\\CSC611M", Integer.toString(port));

        /*Create directory to serve as the file repository of the file server*/
        if (Files.notExists(directory)) {
            Files.createDirectories(directory);
        }

        pool = Executors.newFixedThreadPool(2);

    }

    public boolean isClosed() {
        return server.isClosed();
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

            /*Create a new server socket for this file server*/
            server = new ServerSocket(port);
            String ipAddress = InetAddress.getLocalHost().getHostAddress();
            String localPort = Integer.toString(server.getLocalPort());
            String addressPort = ipAddress + ":" + localPort;
            System.out.println("File server " + addressPort + " started.");

            /*Inform the gateway that this server is alive*/
            URL gateway = new URL("http://localhost:8080/Project/faces/register.xhtml?ip=" + ipAddress + "&port=" + localPort);
            try (InputStream is = gateway.openStream()) {
                System.out.println("File server " + addressPort + " connected to the gateway.");
            } catch (FileNotFoundException e) {
                System.out.println("File server " + addressPort + " cannot connect to the gateway.");
            }
            
            while (true) {
                Socket dest = server.accept();
                pool.execute(new FileServerHandleConnection(dest, information, getFileList()));

            }
        } catch (SocketException ex) {
            //Socket is closed
            System.out.println(ex + " " + information + " closed");
        } catch (IOException ex) {
            Logger.getLogger(FileServer.class.getName()).log(Level.SEVERE, null, ex);
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
