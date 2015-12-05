package advanos;

import advanos.server.FileServerInfo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import rx.Observable;
import rx.Observer;

public class Protocol {

    public static final int FILE_LIST = 1;
    public static final int UPLOAD = 2;
    public static final int DOWNLOAD = 3;
    public static final int COPY_ALL = 4;
    public static final int PASTE_ALL = 5;
    public static final int SERVER_INFO = 6;

    public static final int PING = 7;
    public static int RESPONSE_PING_ALIVE = 0;

    public static final int HAS_FILE = 8;
    public static final int RESPONSE_HAS_FILE = 1;

    public static final int START_PORT = 1099;
    public static final String DIRECTORY = "C:\\CSC611M";

    /**
     * Number of servers
     */
    public static final int NUMBER_OF_SERVERS = 6;

    /**
     * Sends bytes from one stream to another
     *
     * @param inputStream stream to read data from
     * @param outputStream stream to write data to
     * @throws IOException
     */
    public static void transferBytes(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[1024];
        while (inputStream.read(buffer) > -1) {
            outputStream.write(buffer);
        }
        outputStream.flush();
    }

    /**
     * Sends an object
     *
     * @param dest The destination socket
     * @param object The object to be sent
     */
    public static void sendObject(Socket dest, Object object) {
        try {
            ObjectOutputStream oos = new ObjectOutputStream(dest.getOutputStream());
            oos.writeObject(object);
            oos.flush();
        } catch (IOException ex) {
            Logger.getLogger(Protocol.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Initially, this call reads the filename of the file to be received.
     *
     * Once it has the filename, it begins reading the bytes sent as a file and
     * overwrites the data received.
     *
     * @param inputStream The input stream that receiving the data
     * @param information the information describing the file server
     */
    public static void uploadFile(InputStream inputStream, Path directory) throws IOException {
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
            String fileName = bufferedReader.readLine();
            Files.copy(inputStream, directory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);

            /*Establish a URL connection to the gateway to notify that the file was uploded*/
            URL gateway = new URL(URLEncoder.encode("http://localhost:8080/advanos-sync-project-yehey/faces/uploadingfinish.xhtml?file=" + fileName, "UTF-8"));
            try (InputStream connect = gateway.openStream()) {
                
            }
        }
    }

    /**
     * This function immediately receives the file data as bytes from the input
     * stream and saves it in the directory file path specified.
     *
     * @param inputStream The input stream that receiving the data
     * @param directory The base folder where it should be stored
     * @param filename the name of the file
     */
    public static void receiveFile(InputStream inputStream, Path directory, String filename) {
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            Files.copy(inputStream, directory.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Initially, this call reads the filename of the file to be downloaded from
     * a file server.
     *
     * If the file server described by information does not have the specified
     * file, this responds with a 0.
     *
     * If the file server does have the specified file, this responds with 1 and
     * then sends the file data as bytes.
     *
     * @param dest The destination socket
     * @param information the information of the file server
     * @return true if the server described by information has the file
     */
    public static boolean sendRequestedFile(Socket dest, FileServerInfo information) {
        boolean exists = false;
        try {
            OutputStream os = dest.getOutputStream();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(dest.getInputStream()));

            String fileNameOfRequestedFile = bufferedReader.readLine();
            Path fileInDirectory = information.getDirectory().resolve(fileNameOfRequestedFile);
            exists = Files.exists(fileInDirectory);

            /*Send 1 if file exists, 0 if not*/
            os.write(exists ? 1 : 0);
            os.flush();

            /*Perform downloading if file exists*/
            if (exists) {
                sendFileBytes(dest, fileInDirectory);
            }
        } catch (IOException ex) {
            Logger.getLogger(Protocol.class.getName()).log(Level.SEVERE, null, ex);
        }
        return exists;
    }

    /**
     * From a socket, this first reads the first line as the filename, and then
     * responds whether it has the file or not.
     *
     * @param dest the socket requesting if the file available
     * @param information information of the file server
     * @return 1 if available, 0 otherwise
     */
    public static boolean fileExists(Socket dest, FileServerInfo information) {
        boolean exists = false;
        try {
            OutputStream os = dest.getOutputStream();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(dest.getInputStream()));

            String fileName = bufferedReader.readLine();
            Path fileInDirectory = information.getDirectory().resolve(fileName);
            exists = Files.exists(fileInDirectory);

            /*Send 1 if file exists, 0 if not*/
            os.write(exists ? 1 : 0);
            os.flush();

        } catch (IOException ex) {
            Logger.getLogger(Protocol.class.getName()).log(Level.SEVERE, null, ex);
        }
        return exists;
    }

    /**
     *
     * @param dest The destination socket
     * @param file The file to be sent
     */
    public static void sendFileBytes(Socket dest, Path file) {
        try (InputStream fileSelected = Files.newInputStream(file)){
            OutputStream os = dest.getOutputStream();
            byte[] buffer = new byte[1024];
            while (fileSelected.read(buffer) > -1) {
                os.write(buffer);
            }
            os.flush();
        } catch (IOException ex) {
            Logger.getLogger(Protocol.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Copies all files from one server to a selected socket
     *
     * @param dest The destination socket
     * @param information the information of the file server
     */
    public static void copyAll(Socket dest, FileServerInfo information) {
        Set<String> list = information.getFileList();
        sendObject(dest, list);

        list.forEach(filename -> {
            Path fileInDirectory = information.getDirectory().resolve(filename);
            sendFileBytes(dest, fileInDirectory);
        });
    }

    /**
     * Reads a set of string which are the filenames of the files available on
     * the file server connected to the socket
     *
     * @param from the socket to read from
     * @param information the information of the file server
     * @return the file names read from the socket
     */
    public static Set<String> readFileList(Socket from) {
        try {
            ObjectInputStream ois = new ObjectInputStream(from.getInputStream());
            return (Set<String>) ois.readObject();
        } catch (IOException | ClassNotFoundException ex) {
            Logger.getLogger(Protocol.class.getName()).log(Level.SEVERE, null, ex);
        }
        return new HashSet<>();
    }

    /**
     * Reads the file list from a socket, and then for each of the filenames, it
     * saves the data on to the directory of the receiving file server
     *
     * @param from the socket from where the stream of file data will come from
     * @param information information of the file server that will receive the
     * data
     */
    public static void pasteAll(Socket from, FileServerInfo information) {
        Set<String> fileList = readFileList(from);

        fileList.forEach(filename -> {
            try {
                Path fileInDirectory = information.getDirectory().resolve(filename);
                Files.copy(from.getInputStream(), fileInDirectory, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                Logger.getLogger(Protocol.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
    }

    /**
     * Writes a number to a socket
     *
     * @param dest the destination socket
     * @param number the number to be written
     */
    public static void write(Socket dest, int number) {
        try {
            OutputStream os = dest.getOutputStream();
            PrintWriter pw = new PrintWriter(os);
            pw.write(number);
            pw.flush();
        } catch (IOException ex) {
            Logger.getLogger(Protocol.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Writes text and ends it with a new line
     *
     * @param dest
     * @param text
     */
    public static void write(Socket dest, String text) {
        try {
            OutputStream os = dest.getOutputStream();
            PrintWriter pw = new PrintWriter(os);
            pw.println(text);
            pw.flush();
        } catch (IOException ex) {
            Logger.getLogger(Protocol.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Sends a ping request to a server to see if it is alive.
     *
     * @param dest The socket to the file server to see if it is alive
     */
    public static void ping(Socket dest) {
        write(dest, Protocol.PING);
    }

    /**
     * Reads an integer from a socket
     *
     * @param from the socket to read from
     * @return the integer that was read
     */
    public static Integer readNumber(Socket from) {
        return (Integer) Observable.create(subscriber -> {
            try {
                InputStream is = from.getInputStream();
                subscriber.onNext(is.read());
            } catch (Exception e) {
                subscriber.onError(e);
            }

        }).first().toBlocking().first();
    }

    /**
     * Creates a download dialog box for the gateway server
     *
     * @param from the socket that will be sending the file data in bytes
     * @param fileName the name of the file
     * @throws IOException
     */
    public static void deliverFileToBrowser(Socket from, String fileName) throws IOException {
        /*Response header*/
        FacesContext fc = FacesContext.getCurrentInstance();
        ExternalContext ec = fc.getExternalContext();
        ec.responseReset();
        ec.setResponseContentType(ec.getMimeType(fileName));
        ec.setResponseHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

        /*Write bytes*/
        Protocol.transferBytes(from.getInputStream(), ec.getResponseOutputStream());
        fc.responseComplete();
    }

    /**
     * Reads a line from the socket
     *
     * @param dest the socket to read from
     * @return the first line it can read
     */
    public static String readLine(Socket dest) {
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(dest.getInputStream()));

            return bufferedReader.readLine();
        } catch (IOException ex) {
            Logger.getLogger(Protocol.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public static Socket connect(FileServerInfo info) {
        try {
            return new Socket("localhost", info.getPort());
        } catch (IOException ex) {
            Logger.getLogger(Protocol.class.getName()).log(Level.SEVERE, "Failed to create a socket connecting to " + info, ex);
        }
        return null;
    }

    // todo: modify the condition
    public static Integer computeReplicationAmount(Integer aliveServers) {
        return aliveServers;
    }
}
