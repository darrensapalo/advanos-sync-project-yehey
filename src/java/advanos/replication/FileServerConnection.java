/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package advanos.replication;

import advanos.Protocol;
import advanos.server.FileServerInfo;
import java.net.Socket;

/**
 *
 * @author Darren
 */
public class FileServerConnection {

    private final FileServerInfo fileServerInfo;
    private final Socket socket;

    FileServerConnection(int port) {
        fileServerInfo = new FileServerInfo();
        fileServerInfo.setPort(port);
        
        socket = Protocol.connect(fileServerInfo);
    }

    public FileServerInfo getFileServerInfo() {
        return fileServerInfo;
    }

    public Socket getSocket() {
        return socket;
    }
    
    public int getPort(){
        return fileServerInfo.getPort();
    }
    
}
