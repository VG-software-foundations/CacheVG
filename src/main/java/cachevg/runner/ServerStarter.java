package cachevg.runner;

import cachevg.connection.tcp.server.Server;
import cachevg.parser.MessageParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ExecutorService;

public class ServerStarter {

    private static final Logger log = LogManager.getLogger(ServerStarter.class);
    private final ExecutorService executorForProcessing;
    private final Server server;
    private final MessageParser messageParser;

    public ServerStarter(
            ExecutorService executorForProcessing,
            Server server,
            MessageParser msgParser) {
        this.executorForProcessing = executorForProcessing;
        this.server = server;
        this.messageParser = msgParser;
    }

    public void run() {
        log.info("Starting server...");
        executorForProcessing.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    handleConnectedClientsEvents(server);
                    handleDisConnectedClientsEvents(server);
                    handleClientMessages(server);
                    if (executorForProcessing.isShutdown()) {
                        log.info("Server handlers are stopping...");
                        Thread.currentThread().interrupt();
                    }
                } catch (Exception ex) {
                    log.error("Error:{}", ex.getMessage());
                }
            }
        });
        server.start();
    }

    private void handleConnectedClientsEvents(Server server) {
        var newClient = server.getConnectedClientsEvents().poll();
        if (newClient != null) {
            messageParser.newMessage(newClient);
            log.info("connected client:{}", newClient);
        }
    }

    private void handleDisConnectedClientsEvents(Server server) {
        var disconnectedClient = server.getDisconnectedClientsEvents().poll();
        if (disconnectedClient != null) {
            log.info("Disconnected client:{}", disconnectedClient);
            messageParser.cleanMessagesMap(disconnectedClient);
        }
    }

    private void handleClientMessages(Server server) {
        var messageFromClient = server.getMessagesFromClients().poll();
        if (messageFromClient != null) {
            var clientAddress = messageFromClient.clientAddress();
            log.info("{}:, message.length:{}", clientAddress, messageFromClient.message().length);
            var msgs = messageParser.parseMessage(clientAddress, messageFromClient.message());

            for (var msg : msgs) {
                log.info("message:{}", new String(msg));
            }
        }
    }
}