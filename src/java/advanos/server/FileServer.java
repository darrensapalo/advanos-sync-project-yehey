package advanos.server;

import advanos.Protocol;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
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
public class FileServer extends Thread {


    private final ServerSocket server;
    private Path directory;
    private boolean isAlive;

    public FileServer(int port) throws IOException {
        this.server = new ServerSocket(port);
        this.isAlive = false;
        try {
            directory = Paths.get("C:\\CSC611M", Integer.toString(port));

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
                isAlive = true;
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
                            break;
                        case Protocol.SHUTDOWN:
                            try (OutputStream os = accept.getOutputStream(); PrintWriter pw = new PrintWriter(os)) {
                                pw.println(Protocol.OK);
                                pw.flush();
                            }
                            server.close();
                            break;
                    }
                }
            }
        } catch (SocketException ex) {

        } catch (IOException ex) {
            Logger.getLogger(GatewayServer.class.getName()).log(Level.SEVERE, null, ex);
        }
        isAlive = false;
    }

    void stopServer() throws IOException {
        server.close();
    }

    public boolean isStopped(){
        return server.isClosed();
    }
    
    public boolean isServerAlive(){
        return isAlive;
    }
    
    
    private static void create(int port) {
        try {
            FileServer fileServer = new FileServer(port);
            fileServer.start();
        } catch (IOException ex) {
            Logger.getLogger(FileServer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public static void main(String[] args) {
        if (args.length != 1){
            System.err.println("Please input the ID of this FileServer (e.g. 0).");
            System.exit(1);
        }
        
        try {
            int port = Integer.parseInt(args[0]);
            FileServer.create(port);
        }catch(NumberFormatException e){
            System.err.println("Please input a valid ID for this FileServer (e.g. 0).");
            System.exit(2);
        }
    }
}
