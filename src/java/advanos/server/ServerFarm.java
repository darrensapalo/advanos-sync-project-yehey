package advanos.server;

import advanos.Protocol;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerFarm {

    private ExecutorService pool;
    private FileServer[] servers;

    public ServerFarm() {
        pool = Executors.newFixedThreadPool(Protocol.NUMBER_OF_SERVERS);
        servers = new FileServer[Protocol.NUMBER_OF_SERVERS];

        /*Start all servers*/
        for (int i = 0; i < Protocol.NUMBER_OF_SERVERS; i++) {
            int port = Protocol.START_PORT + i;
            try {
                servers[i] = new FileServer(port);
                start(servers[i]);
            } catch (IOException ex) {
                Logger.getLogger(GatewayServer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

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

    public final void start(FileServer server) {
        try {
            pool.execute(server.start());
        } catch (IOException ex) {
            Logger.getLogger(ServerFarm.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public FileServer[] getServers() {
        return servers;
    }
}
