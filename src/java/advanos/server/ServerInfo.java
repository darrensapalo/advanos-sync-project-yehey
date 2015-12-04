package advanos.server;

public class ServerInfo {

    private final String ipAddress;
    private final int port;

    public ServerInfo(String ipAddress, int port) {
        this.ipAddress = ipAddress;
        this.port = port;
    }

    public String getIPAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }
}
