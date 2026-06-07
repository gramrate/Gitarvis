package src.input;

import java.util.Optional;

public interface InputSource {
    Optional<String> read();
}
