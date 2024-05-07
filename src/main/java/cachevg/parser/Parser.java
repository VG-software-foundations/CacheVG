package cachevg.parser;

public interface Parser <F, T> {
    T parse(F from);
}
