/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package advanos.server;

import advanos.Protocol;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.net.Socket;
import java.util.Set;

/**
 *
 * @author Darren
 */
public class FileServerHandleConnection extends Thread {

    private final Socket dest;
    private final FileServerInfo information;
    private final Set<String> fileList;

    FileServerHandleConnection(Socket dest, FileServerInfo information, Set<String> fileList) {
        this.dest = dest;
        this.information = information;
        this.fileList = fileList;
    }

    @Override
    public void run() {
        try {
            InputStream inputStream = dest.getInputStream();
            BufferedInputStream bis = new BufferedInputStream(inputStream);
            DataInputStream dis = new DataInputStream(bis);

            int input = dis.readInt();
            System.out.println("Received a connection with request " + input);

            switch (input) {
                case Protocol.SERVER_INFO:
                    Protocol.sendObject(dest, information);
                    break;

                case Protocol.FILE_LIST:
                    Protocol.sendObject(dest, fileList);
                    break;

                case Protocol.UPLOAD:
                    Protocol.uploadFile(dis, dest, information.getDirectory());

                    break;
                case Protocol.DOWNLOAD:
                    Protocol.sendRequestedFile(dis, dest, information);
                    break;

                /*Sends all files of this server starting with a set of file names*/
                case Protocol.COPY_ALL:
                    Protocol.copyAll(dest, information);
                    break;

                /*Receives all files of this server starting with a set of file names*/
                case Protocol.PASTE_ALL:
                    Protocol.pasteAll(dest, information);
                    break;

                /* Responds with 0 if there are no issues */
                case Protocol.PING:
                    Protocol.write(dest, 0);
                    break;

                case Protocol.HAS_FILE:
                    String fileName = Protocol.readLine(dis);
                    if (fileList.contains(fileName)) {
                        Protocol.write(dest, Protocol.RESPONSE_HAS_FILE);
                    } else {
                        Protocol.write(dest, 0);
                    }
                    break;
            }
        } catch (Exception e) {

        }

    }

}
