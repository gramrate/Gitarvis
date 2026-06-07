package src.input;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

final class InputSourceTest {
    @Test
    void defaultCloseIsNoOp() {
        InputSource inputSource = () -> Optional.empty();

        assertDoesNotThrow(inputSource::close);
    }
}
