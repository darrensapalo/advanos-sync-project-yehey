package advanos.replication;


import advanos.Protocol;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.util.Set;

public class ReplicationService {
    
    
    
    public static void main(String[] args) {
        try (Socket server = new Socket("localhost", 1099)) {
            System.out.println("ggg");
        } catch (ConnectException e) {
            System.out.println("Failed");
            System.out.println(e + " first");
            try (Socket server2 = new Socket("localhost", 1100);
                    OutputStream os = server2.getOutputStream();
                    InputStream is = server2.getInputStream();
                    ObjectInputStream ois = new ObjectInputStream(is)) {
                System.out.println("then");
                os.write(Protocol.COPY_ALL);
                os.flush();
                Set<String> fileNames = (Set<String>) ois.readObject();
                try (Socket server3 = new Socket("localhost", 1101);
                        OutputStream os3 = server3.getOutputStream();
                        ObjectOutputStream oos = new ObjectOutputStream(os3)) {
                    os.write(Protocol.PASTE_ALL);
                    oos.writeObject(oos);
                    oos.flush();
                    for (String file : fileNames) {
                        byte[] buffer = new byte[1024];
                        while (is.read(buffer) > -1) {
                            os3.write(buffer);
                        }
                        os3.flush();
                    }
                }
            } catch (ClassNotFoundException | IOException ex) {
                System.out.println(ex + " second");
                e.printStackTrace();
            }
        } catch (IOException e) {
            System.out.println(e);
        }
    }
}
