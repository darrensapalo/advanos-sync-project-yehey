package advanos.server;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.faces.bean.ApplicationScoped;
import javax.faces.bean.ManagedBean;

/**
 * Controller for file servers simulation
 *
 * @author CSC611M G01
 */
@ManagedBean(eager = false)
@ApplicationScoped
public class ServerController {

    /**
     * The file server farm.
     */
    private ServerFarm farm;

    /**
     * Generate the file server farm.
     */
    @PostConstruct
    public void init() {
        try {
            farm = new ServerFarm();
        } catch (IOException ex) {
            Logger.getLogger(ServerController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Close all file servers and shutdown thread pool.
     */
    @PreDestroy
    public void cleanup() {
        farm.close();
    }

    /**
     * Get the file server farm to be used in the web site.
     * @return The file server farm
     */
    public ServerFarm getFarm() {
        return farm;
    }
}
