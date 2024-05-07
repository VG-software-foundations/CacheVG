package cachevg.connection.tcp.server;

import cachevg.connection.tcp.server.message.ClientMessage;

import java.net.SocketAddress;
import java.util.Queue;

public interface Server {
    void start();

    void stop();

    Queue<SocketAddress> getConnectedClientsEvents();

    Queue<SocketAddress> getDisconnectedClientsEvents();

    Queue<ClientMessage> getMessagesFromClients();

    boolean send(SocketAddress clientAddress, byte[] data);
}
