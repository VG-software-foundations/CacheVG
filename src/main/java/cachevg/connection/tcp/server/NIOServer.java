package cachevg.connection.tcp.server;

import cachevg.connection.tcp.server.message.ClientMessage;
import cachevg.connection.tcp.server.message.ServerMessage;
import cachevg.exception.technical.ClientCommunicationException;
import cachevg.exception.technical.ServerProcessingError;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sun.misc.Unsafe;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class NIOServer implements Server {
    private static final Logger log = LogManager.getLogger(NIOServer.class);
    private static final byte[] EMPTY_ARRAY = new byte[0];
    private static final int TIME_OUT_MS = 100;

    private final Unsafe unsafe;
    private final int port;
    private final InetAddress addr;
    private long clientMessagesCounter;
    private long clientBytesCounter;

    private final Map<SocketAddress, SocketChannel> clients = new HashMap<>();
    private final Queue<SocketAddress> connectedClientsEvents = new ConcurrentLinkedQueue<>();
    private final Queue<SocketAddress> disconnectedClientsEvents = new ConcurrentLinkedQueue<>();
    private final Queue<ServerMessage> messagesForClients = new ArrayBlockingQueue<>(1000);
    private final Queue<ClientMessage> messagesFromClients = new ArrayBlockingQueue<>(1000);

    private static final int MESSAGE_SIZE_LIMIT_BYTES = 102_400;
    private final ByteBuffer buffer = ByteBuffer.allocate(1024);

    private final List<ByteBuffer> parts = new ArrayList<>();
    private volatile boolean active = true;

    public NIOServer(int port) {
        this(null, port);
    }

    public NIOServer(InetAddress addr, int port) {
        try {
            Constructor<Unsafe> unsafeConstructor = Unsafe.class.getDeclaredConstructor();
            unsafeConstructor.setAccessible(true);
            unsafe = unsafeConstructor.newInstance();
        } catch (Exception ex) {
            throw new ServerProcessingError(ex);
        }

        log.debug("addr:{}, port:{}", addr, port);
        this.addr = addr;
        this.port = port;
    }


    @Override
    public void start() {
        try {
            try (var serverSocketChannel = ServerSocketChannel.open()) {
                serverSocketChannel.configureBlocking(false);
                var serverSocket = serverSocketChannel.socket();
                serverSocket.bind(new InetSocketAddress(addr, port));
                try (var selector = Selector.open()) {
                    serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
                    log.info("Started server at addr:{}, port:{}", addr, port);
                    while (active) {
                        handleSelector(selector);
                    }
                    log.info("Server stopped. addr:{}, port:{}", addr, port);
                }
            }
        } catch (Exception ex) {
            log.error("Error. addr:{}, port:{}", addr, port, ex);
            throw new ServerProcessingError(ex);
        }
    }

    @Override
    public void stop() {
        log.info("<STOP> command received. addr:{}, port:{}", addr, port);
        active = false;
    }

    @Override
    public Queue<SocketAddress> getConnectedClientsEvents() {
        return connectedClientsEvents;
    }

    @Override
    public Queue<SocketAddress> getDisconnectedClientsEvents() {
        return disconnectedClientsEvents;
    }

    @Override
    public Queue<ClientMessage> getMessagesFromClients() {
        return messagesFromClients;
    }

    @Override
    public boolean send(SocketAddress clientAddress, byte[] data) {
        var result = messagesForClients.offer(new ServerMessage(clientAddress, data));
        log.debug("Scheduled for sending to the client:{}, result:{}", clientAddress, result);
        return result;
    }

    private void handleSelector(Selector selector) {
        try {
            selector.select(this::performIO, TIME_OUT_MS);
            sendMessagesToClients();
        } catch (ClientCommunicationException ex) {
            var clintAddress = getSocketAddress(ex.getSocketChannel());
            log.error("Error in client communication:{}", clintAddress, ex);
            disconnect(clintAddress);
        } catch (Exception ex) {
            log.error("Unexpected error:{}", ex.getMessage(), ex);
        }
    }

    private void performIO(SelectionKey selectedKey) {
        if (selectedKey.isAcceptable()) {
            acceptConnection(selectedKey);
        } else if (selectedKey.isReadable()) {
            readFromClient(selectedKey);
        }
    }

    private void acceptConnection(SelectionKey key) {
        var serverSocketChannel = (ServerSocketChannel) key.channel();
        try {
            var clientSocketChannel = serverSocketChannel.accept();
            var selector = key.selector();
            log.debug(
                    "Accept client connection: key:{}, selector:{}, clientSocketChannel:{}",
                    key,
                    selector,
                    clientSocketChannel);

            clientSocketChannel.configureBlocking(false);
            clientSocketChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

            var remoteAddress = clientSocketChannel.getRemoteAddress();
            clients.put(remoteAddress, clientSocketChannel);
            connectedClientsEvents.add(remoteAddress);
        } catch (Exception ex) {
            log.error("Can't accept new client on:{}", key);
        }
    }

    private void disconnect(SocketAddress clientAddress) {
        var clientChannel = clients.remove(clientAddress);
        if (clientChannel != null) {
            try {
                clientChannel.close();
            } catch (IOException e) {
                log.error("clientChannel:{}, closing error:{}", clientAddress, e.getMessage(), e);
            }
        }
        log.debug(
                "messagesFromClientsCounter:{}, bytesFromClientsCounter:{}",
                clientMessagesCounter,
                clientBytesCounter);
        disconnectedClientsEvents.add(clientAddress);
    }

    private void readFromClient(SelectionKey selectionKey) {
        var socketChannel = (SocketChannel) selectionKey.channel();
        log.debug("{}. Reading from client", socketChannel);

        var data = readRequest(socketChannel);
        clientBytesCounter += data.length;
        if (data.length == 0) {
            disconnect(getSocketAddress(socketChannel));
        } else {
            clientMessagesCounter++;
            messagesFromClients.add(new ClientMessage(getSocketAddress(socketChannel), data));
        }
    }

    private SocketAddress getSocketAddress(SocketChannel socketChannel) {
        try {
            return socketChannel.getRemoteAddress();
        } catch (Exception ex) {
            throw new ClientCommunicationException("Get RemoteAddress error", ex, socketChannel);
        }
    }

    private byte[] readRequest(SocketChannel socketChannel) {
        try {
            int usedIdx = 0;
            int readBytesTotal = 0;
            int readBytes;
            while (readBytesTotal < MESSAGE_SIZE_LIMIT_BYTES && (readBytes = socketChannel.read(buffer)) > 0) {
                buffer.flip();
                if (usedIdx >= parts.size()) {
                    parts.add(ByteBuffer.allocateDirect(readBytes));
                }

                if (parts.get(usedIdx).capacity() < readBytes) {
                    unsafe.invokeCleaner(parts.get(usedIdx));
                    parts.add(usedIdx, ByteBuffer.allocateDirect(readBytes));
                }

                parts.get(usedIdx).put(buffer);
                buffer.flip();
                readBytesTotal += readBytes;
                usedIdx++;
            }
            log.debug("Reading bytes:{}, usedIdx:{}", readBytesTotal, usedIdx);

            if (readBytesTotal == 0) {
                return EMPTY_ARRAY;
            }
            var result = new byte[readBytesTotal];
            var resultIdx = 0;

            for (var idx = 0; idx < usedIdx; idx++) {
                var part = parts.get(idx);
                part.flip();
                part.get(result, resultIdx, part.limit());
                resultIdx += part.limit();
                part.flip();
            }
            return result;
        } catch (Exception ex) {
            throw new ClientCommunicationException("Error occurred while reading the message", ex, socketChannel);
        }
    }

    private void sendMessagesToClients() {
        ServerMessage msg;
        while ((msg = messagesForClients.poll()) != null) {
            var client = clients.get(msg.clientAddress());
            if (client == null) {
                log.error("Client {} not found", msg.clientAddress());
            } else {
                write(client, msg.message());
            }
        }
    }

    private void write(SocketChannel clientChannel, byte[] data) {
        log.debug("Writing to client:{}, data.length:{}", clientChannel, data.length);
        var bufferForWrite = ByteBuffer.allocate(data.length);
        bufferForWrite.put(data);
        bufferForWrite.flip();
        try {
            clientChannel.write(bufferForWrite);
        } catch (Exception ex) {
            throw new ClientCommunicationException("Write to the client error", ex, clientChannel);
        }
    }
}
