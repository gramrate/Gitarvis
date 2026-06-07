package src.input;

import java.util.Optional;

public interface InputSource extends AutoCloseable {
    Optional<String> read();

    @Override
    default void close() {
    }
}
