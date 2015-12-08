package advanos.server;

import advanos.Protocol;
import java.io.IOException;
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
}
