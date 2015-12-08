/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package advanos.replication.observers;

import advanos.Protocol;
import advanos.gateway.ServerInfo;
import advanos.server.FileServerInfo;
import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import rx.Observable;

/**
 *
 * @author Darren
 */
public class AliveServersObserver {

    public static Observable<ServerInfo> create(Set<ServerInfo> infos) {
        return Observable.from(infos)
                // Get all the alive servers
                .filter(c -> {
                    try (Socket socket = c.getSocket()){
                        
                        Protocol.ping(socket);
                        boolean isAlive = Protocol.readNumber(socket) == Protocol.RESPONSE_PING_ALIVE;
                        return isAlive;
                    } catch (ConnectException ex) {
                        return false;
                    } catch (IOException ex) {
                        Logger.getLogger(AliveServersObserver.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    return false;
                })
                .retryWhen(new RetryWithDelay(3, 2000));
    }
}
