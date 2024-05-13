package cachevg.processor;

public class UnknownCommandProcessor implements Processor {
    @Override
    public String process(String[] args) {
        return "Command " + args[0] + " is not supported";
    }
}
