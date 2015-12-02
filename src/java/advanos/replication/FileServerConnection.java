/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package advanos.replication;

import advanos.Protocol;
import advanos.server.FileServerInfo;
import java.io.IOException;
import java.net.Socket;

/**
 *
 * @author Darren
 */
public class FileServerConnection {

    private final FileServerInfo fileServerInfo;
    private Socket socket;

    FileServerConnection(int port) {
        fileServerInfo = new FileServerInfo();
        fileServerInfo.setPort(port);
        
        
    }

    public FileServerInfo getFileServerInfo() {
        return fileServerInfo;
    }

    public Socket getSocket() throws IOException {
        if (socket == null || socket.isClosed())
            socket = Protocol.connect(fileServerInfo);
        return socket;
    }
    
    public int getPort(){
        return fileServerInfo.getPort();
    }
    
}
