package cachevg.processor;

import java.util.HashMap;
import java.util.Map;

import static cachevg.command.CommandNames.*;

public class CommandMapper {
    private static Map<String, Processor> processors = new HashMap<>();

    static {
        processors.put(PUT, new PutCommandProcessor());
        processors.put(REMOVE, new RemoveCommandProcessor());
        processors.put(GET, new GetCommandProcessor());
        processors.put(KEYS, new KeysCommandProcessor());
        processors.put(PING, new PingCommandProcessor());
    }

    public static Processor mapCommandToProcessor(String name) {
        Processor processor = processors.get(name.toUpperCase());
        return processor == null ? new UnknownCommandProcessor() : processor;
    }

}
