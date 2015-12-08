package advanos.server;

import advanos.Protocol;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides simulation for file servers hosted in one machine.
 *
 * @author CSC611M G01
 */
public class ServerFarm {

    /**
     * Thread pool for the file server to run into
     */
    private final ExecutorService pool;

    /**
     * File servers
     */
    private final FileServer[] servers;

    /**
     * Generates all file servers and provide the thread pool for these file
     * servers to run into.
     *
     * @throws java.io.IOException If there is an connection problem with
     * file servers
     */
    public ServerFarm() throws IOException {
        pool = Executors.newFixedThreadPool(Protocol.NUMBER_OF_SERVERS);
        servers = new FileServer[Protocol.NUMBER_OF_SERVERS];

        /*Start all servers*/
        for (int i = 0; i < Protocol.NUMBER_OF_SERVERS; i++) {
            int port = Protocol.START_PORT + i;
            servers[i] = new FileServer(port);
            start(servers[i]);
        }
    }

    /**
     * Close all file servers and shutdown thread pool.
     */
    public void close() {
        Arrays.stream(servers).forEach(server -> {
            try {
                server.stop();
            } catch (IOException ex) {
                Logger.getLogger(ServerController.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        pool.shutdown();
    }

    /**
     * Starts the selected file server and putting it on the thread pool.
     *
     * @param server The file server being selected
     */
    public final void start(FileServer server) {
        pool.execute(server);
    }

    /**
     * Gets all servers.
     *
     * @return All servers
     */
    public FileServer[] getServers() {
        return servers;
    }

    public static void main(String[] args) {
        try {
            ServerFarm servers = new ServerFarm();
            System.out.println("File servers initialized...");
            try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
                String input;
                do {
                    Thread.sleep(2000);
                    System.out.println();

                    /*Status of each port*/
                    Arrays.stream(servers.servers).forEach(server -> System.out.println(server.getPort() + " " + (server.isClosed() ? "closed" : "open")));
                    System.out.println("Syntax [stop|start] <port number> or \"exit\" to quit.");
                    System.out.print("Enter input: ");    //stop 1099
                    input = br.readLine();

                    /*Get the file server based on the second input after "stop" or "start" keyword*/
                    if (input != null && !"exit".equalsIgnoreCase(input)) {
                        String[] token = input.split("\\s+");
                        int port = Integer.parseInt(token[1]);
                        FileServer server = Arrays.stream(servers.servers)
                                .filter(fileServer -> fileServer.getPort() == port)
                                .findAny()
                                .get();

                        /*Operation commands based on the first argument*/
                        if ("stop".equalsIgnoreCase(token[0])) {
                            server.stop();
                        } else if ("start".equalsIgnoreCase(token[0])) {
                            servers.pool.execute(server);
                        }
                    }
                } while (input != null && !"exit".equalsIgnoreCase(input));
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> servers.close()));
            System.out.println("Program exit.");
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }
}
