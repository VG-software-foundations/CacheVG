package cachevg;

import cachevg.config.ServerConfig;
import cachevg.config.ServerStartupProperties;
import cachevg.connection.tcp.server.Server;
import cachevg.parser.MessageParser;
import cachevg.parser.YamlParser;
import cachevg.runner.ServerStarter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ExecutorService;

public class Gate {

    private static final Logger logger = LogManager.getLogger(Gate.class);

    public static void main(String[] args) {
        com.sun.management.OperatingSystemMXBean os = (com.sun.management.OperatingSystemMXBean)
                java.lang.management.ManagementFactory.getOperatingSystemMXBean();

        logger.info("availableProcessors:{}", Runtime.getRuntime().availableProcessors());
        logger.info("TotalMemorySize, mb:{}", os.getTotalMemorySize() / 1024 / 1024);
        logger.info("maxMemory, mb:{}", Runtime.getRuntime().maxMemory() / 1024 / 1024);
        logger.info("freeMemory, mb:{}", Runtime.getRuntime().freeMemory() / 1024 / 1024);

        ServerStartupProperties properties = new YamlParser().parse("/Users/vitali/Downloads/CacheVG/src/main/resources/properties.yaml");
        ServerConfig config = new ServerConfig(properties);
        Server server = config.server();
        MessageParser parser = config.parser();
        ExecutorService executorService = config.executorForProcessing();

        new ServerStarter(
                executorService,
                server,
                parser
        ).run();
    }
}
