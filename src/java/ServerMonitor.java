
import advanos.Protocol;
import advanos.server.FileServer;
import advanos.server.GatewayServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author Octaviano
 */
public class ServerMonitor {

    public static void main(String[] args) throws IOException {
        int counterOfTime = 0;
        int serverNumber = 5;
        int twoThirds = Math.floorDiv(serverNumber * 2, 3);
        int oneThird = Math.floorDiv(serverNumber * 1, 3);
        int serverSize = 0;

        int[] ports;
        ExecutorService pool;
        FileServer[] servers;
//        int failureCounter = 0;

        int[][] serverPorts = new int[serverNumber][2];

        /*Start all servers*/
        ports = new int[6];
        pool = Executors.newFixedThreadPool(6);
        servers = new FileServer[6];
        for (int i = 0; i < serverNumber; i++) {
            ports[i] = 1099 + i;
            try {
                servers[i] = new FileServer(ports[i]);
                servers[i].start();
                pool.execute(servers[i]);
            } catch (IOException ex) {
                Logger.getLogger(GatewayServer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        for (int i = 0; i < serverPorts.length; i++) {
            int port = 1099 + i;
            serverPorts[i][0] = port;
            serverPorts[i][1] = 0;
        }

        while (true) {

            //if failed, increment.
//            for (int counter = 0; counter < serverNumber; counter++) {
//                boolean isSocketConnected = new Socket("localhost", serverPorts[counter][0]).isConnected();
//                if (isSocketConnected == false) {
//                    serverPorts[counter][1]++;
//                }
//
//            }
            for (int i = 0; i < twoThirds; i++) {
                int port = 1099 + i;
                try {
                    new Socket("localhost", port);
                } catch (ConnectException e) {
                    // get port of which server can copy.
                    int openPort = getOpenPort(twoThirds, serverPorts);
                    System.out.println("Ano ang openPort na puwede? " + openPort);

                    Socket socket = new Socket("localhost", openPort);
                    OutputStream output = socket.getOutputStream();
                    output.write(Protocol.FILE_LIST);
                    System.out.println("Protocol=  " + Protocol.FILE_LIST);
                    output.flush();

                    // File list from gateway server.
                    try (InputStream is = socket.getInputStream();
                            ObjectInputStream ois = new ObjectInputStream(is)) {
                        Set<String> fileNames = (Set<String>) ois.readObject();
                        serverSize = fileNames.size();
                        System.out.println("Ilan ang laman niya? " + serverSize);
                        //replicating the files to the back-up
                        replicateToBackup(oneThird, serverSize, serverPorts, fileNames, is);

                    } catch (ClassNotFoundException | IOException ex) {
                        System.out.println("may error puta");
                        Logger.getLogger(GatewayServer.class.getName()).log(Level.SEVERE, null, ex);
                    }

                }
            }

            try {
                Thread.sleep(6000);
            } catch (InterruptedException ex) {
                Logger.getLogger(ServerMonitor.class.getName()).log(Level.SEVERE, null, ex);
            }
            System.out.println("time: " + (counterOfTime++));
            if (counterOfTime == 1) {
                System.out.println("Bukas si " + servers[0] + " ...STOP");
                servers[0].stop();
            }
            if (counterOfTime == 2) {
                System.exit(0);
            }
        }
    }

    /**
     * Get the open port from the 2/3 of the server count.
     */
    public static int getOpenPort(int twoThirds, int[][] serverPorts) throws IOException {
        for (int i = 0; i < twoThirds; i++) {
            try {
                boolean isSocketConnected = new Socket("localhost", serverPorts[i][0]).isConnected();
                if (isSocketConnected == true) {
                    return serverPorts[i][0];
                }
            } catch (Exception e) {
            }
        }
        return -1;
    }

    public static void replicateToBackup(int oneThird, int backupSize, int[][] serverPorts, Set<String> fileNames, InputStream is) throws IOException {

        System.out.println("one :" + oneThird);
        System.out.println("backup " + backupSize);
        System.out.println("file names:" + fileNames.size());
        for (int counter = 0; counter < oneThird; counter++) {

            try (Socket socket = new Socket("localhost", serverPorts[counter][0])) {
                if (backupSize == 0) {
                    System.out.println("dito na ako");
                    OutputStream output = socket.getOutputStream();
                    output.write(Protocol.COPY_ALL);
                    output.flush();

                    try (ObjectOutputStream oos = new ObjectOutputStream(output)) {
                        oos.writeObject(fileNames);
                        oos.flush();

                        //TODO
                        // Check this.
                        for (String fileName : fileNames) {
                            byte[] buffer = new byte[1024];
                            while (is.read(buffer) > -1) {
                                output.write(buffer);
                            }
                            output.flush();

                        }

                    } catch (IOException ex) {
                        Logger.getLogger(GatewayServer.class
                                .getName()).log(Level.SEVERE, null, ex);
                    }

                }

            } catch (IOException ioEx) {
            } catch (Exception e) {
            }

        }
    }
}
