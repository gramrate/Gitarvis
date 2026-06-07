package src.domain.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class CommandInterpretationTest {
    @Test
    void nullValuesAreNormalized() {
        CommandInterpretation interpretation = new CommandInterpretation(null, null, 0.5, null, false);

        assertEquals(CommandAction.UNKNOWN, interpretation.action());
        assertEquals("{RESULT}", interpretation.reply());
        assertEquals("", interpretation.parameter("missing"));
    }

    @Test
    void parametersAreDefensivelyCopied() {
        Map<String, String> source = new java.util.LinkedHashMap<>();
        source.put("message", "one");

        CommandInterpretation interpretation = new CommandInterpretation(CommandAction.COMMIT, source, 1, "ok", false);
        source.put("message", "two");

        assertEquals("one", interpretation.parameter("message"));
        assertThrows(UnsupportedOperationException.class, () -> interpretation.parameters().put("x", "y"));
    }
}
