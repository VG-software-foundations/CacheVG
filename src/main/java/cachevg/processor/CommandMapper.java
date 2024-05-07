package cachevg.processor;

import java.util.HashMap;
import java.util.Map;

public class CommandMapper {
    private static Map<String, Processor> processors = new HashMap<>();

    static {

    }

    public static Processor mapCommandToProcessor(String name) {
        Processor processor = processors.get(name.toUpperCase());
        return processor == null ? new UnknownCommandProcessor() : processor;
    }

}
