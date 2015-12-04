package advanos.server;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.faces.bean.ApplicationScoped;
import javax.faces.bean.ManagedBean;

@ManagedBean
@ApplicationScoped
public class ServerController {

    private ServerFarm farm;

    @PostConstruct
    public void init() {
        farm = new ServerFarm();
    }

    /**
     * Close all file servers and shutdown thread pool.
     */
    @PreDestroy
    public void cleanup() {
        farm.close();
    }

    public ServerFarm getFarm() {
        return farm;
    }
}
