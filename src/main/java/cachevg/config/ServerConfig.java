package cachevg.config;

import cachevg.connection.tcp.server.NIOServer;
import cachevg.connection.tcp.server.Server;
import cachevg.parser.AutomataParser;
import cachevg.parser.MessageParser;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerConfig {
    private final ServerStartupProperties properties;

    public ServerConfig(ServerStartupProperties properties) {
        this.properties = properties;
    }

    public Server server() {
        var server = new NIOServer(properties.getPort());
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        return server;
    }

    public ExecutorService executorForProcessing() {
        var factory = Thread.ofPlatform().name("processor-", 0).factory();
        return Executors.newSingleThreadExecutor(factory);
    }

    public MessageParser parser() {
        return new AutomataParser();
    }
}
