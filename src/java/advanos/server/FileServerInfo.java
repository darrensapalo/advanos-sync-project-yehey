/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package advanos.server;

import advanos.Protocol;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 *
 * @author Darren
 */
public class FileServerInfo {

    private int port = -1;
    private final String ipAddress;

    public Path getDirectory() {
        if (port == -1)
            throw new IllegalStateException("Cannot request for directory if the FileServerInfo is not yet initialized.");
        
        return Paths.get(Protocol.DIRECTORY, Integer.toString(port));
    }

    public FileServerInfo(String ipAddress, int port) {
        this.ipAddress = ipAddress;
        this.port = port;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 19 * hash + Objects.hashCode(this.ipAddress);
        hash = 19 * hash + this.port;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof FileServerInfo) {
            final FileServerInfo other = (FileServerInfo) obj;
            return Objects.equals(ipAddress, other.ipAddress) && port == other.port;
        }
        return false;
    }

    /**
     * Generates a socket from this server.
     *
     * @return the socket that can be used for connection
     * @throws IOException if there are errors on generating a socket
     */
    public Socket getSocket() throws IOException {
        return new Socket(ipAddress, port);
    }

    @Override
    public String toString() {
        return ipAddress + ':' + port;
    }
}
