package src.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GitResultTest {
    @Test
    void resultTextUsesTrimmedOutputWhenPresent() {
        assertEquals("done", new GitResult(true, 0, " done \n").resultText());
    }

    @Test
    void resultTextDescribesEmptySuccess() {
        assertEquals("Команда выполнена успешно", new GitResult(true, 0, "").resultText());
    }

    @Test
    void resultTextDescribesEmptyFailure() {
        assertEquals("Команда завершилась с кодом 7", new GitResult(false, 7, null).resultText());
    }

    @Test
    void skippedUsesFailureCode() {
        GitResult skipped = GitResult.skipped("need data");

        assertEquals(false, skipped.success());
        assertEquals(-1, skipped.exitCode());
        assertEquals("need data", skipped.output());
    }
}
