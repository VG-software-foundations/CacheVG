package cachevg.parser;

import cachevg.config.ServerStartupProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class YamlParser implements Parser <String, ServerStartupProperties> {
    private static final Logger log = LogManager.getLogger(YamlParser.class);
    @Override
    public ServerStartupProperties parse(String from) {
        ServerStartupProperties properties = null;
        String currentDirectory = System.getProperty("user.dir");
        String propertiesPath = currentDirectory + from;
        try (InputStream input = new FileInputStream(propertiesPath)) {
            Yaml yaml = new Yaml(new Constructor(ServerStartupProperties.class, new LoaderOptions()));
            properties = yaml.load(input);
        } catch (IOException ex) {
             log.error(ex.getMessage());
        }
        return properties;
    }
}
