
import advanos.Protocol;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.util.Set;

public class NewClass {

    public static void main(String[] args) {
        try (Socket server = new Socket("localhost", 1099)) {
            System.out.println("ggg");
        } catch (ConnectException e) {
            System.out.println("Failed");
            System.out.println(e + " first");
            try (Socket server2 = new Socket("localhost", 1100);
                    OutputStream os = server2.getOutputStream();
                    InputStream is = server2.getInputStream()) {
                System.out.println("ggg");
                os.write(Protocol.COPY_ALL);
                os.flush();
                try (ObjectInputStream ois = new ObjectInputStream(is)) {
                    Set<String> fileNames = (Set<String>) ois.readObject();
                    try (Socket server3 = new Socket("localhost", 1101);
                        OutputStream os3 = server3.getOutputStream();
                        ObjectOutputStream oos = new ObjectOutputStream(os3)) {
                        os3.write(Protocol.PASTE_ALL);
                        os3.flush();
                        oos.writeObject(fileNames);
                        oos.flush();
                    }
                }
            } catch (ClassNotFoundException | IOException ef) {
                ef.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
