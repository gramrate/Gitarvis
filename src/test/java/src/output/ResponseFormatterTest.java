package src.output;

import org.junit.jupiter.api.Test;
import src.domain.model.GitResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ResponseFormatterTest {
    private final ResponseFormatter formatter = new ResponseFormatter();

    @Test
    void replacesResultPlaceholder() {
        String result = formatter.format("Готово: {RESULT}", new GitResult(true, 0, "ok"));

        assertEquals("Готово: ok", result);
    }

    @Test
    void appendsResultWhenTemplateHasNoPlaceholder() {
        String result = formatter.format("Готово", new GitResult(true, 0, "ok"));

        assertEquals("Готово" + System.lineSeparator() + "ok", result);
    }

    @Test
    void appliesMessageAndNameParameters() {
        String result = formatter.format(
                "{MSG} -> {NAME}: {RESULT}",
                new GitResult(true, 0, "ok"),
                Map.of("message", "commit", "name", "feature")
        );

        assertEquals("commit -> feature: ok", result);
    }

    @Test
    void blankTemplateFallsBackToResultOnly() {
        String result = formatter.format("", new GitResult(true, 0, "ok"));

        assertEquals("ok", result);
    }
}
