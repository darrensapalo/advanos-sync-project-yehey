/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package advanos;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 *
 * @author Darren
 */
public class Protocol {
    
    public static final String DOWNLOAD = "download";
    public static final String FILE_LIST = "file list";
    public static final String UPLOAD = "upload";
    
    
    public static void transferBytes(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[1024];
        while (is.read(buffer) > -1) {
            os.write(buffer);
        }
        os.flush();
    }
        
}
