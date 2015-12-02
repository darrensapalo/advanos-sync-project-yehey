/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package advanos.server;

import advanos.Protocol;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 *
 * @author Darren
 */
public class FileServerInfo {

    private Set<String> fileList;
    private int port = -1;

    void setFileList(Set<String> fileList) {
        this.fileList = fileList;
    }

    void setPort(int port) {
        this.port = port;
    }

    public Path getDirectory() {
        if (port == -1)
            throw new IllegalStateException("Cannot request for directory if the FileServerInfo is not yet initialized.");
        
        return Paths.get(Protocol.DIRECTORY, Integer.toString(port));
    }

    public Set<String> getFileList() {
        return fileList;
    }

    public int getPort() {
        return port;
    }
    
}
