package src.domain;

import org.junit.jupiter.api.Test;
import src.domain.model.CommandAction;
import src.domain.model.CommandInterpretation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CommandParserTest {
    private final CommandParser parser = new CommandParser();

    @Test
    void parsesFullJsonCommand() {
        CommandInterpretation result = parser.parse("""
                {
                  "action": "add_commit",
                  "parameters": {"message": "initial work"},
                  "confidence": 0.97,
                  "reply": "ok {MSG} {RESULT}",
                  "need_input": false
                }
                """);

        assertEquals(CommandAction.ADD_COMMIT, result.action());
        assertEquals("initial work", result.parameter("message"));
        assertEquals(0.97, result.confidence(), 0.0001);
        assertEquals("ok {MSG} {RESULT}", result.reply());
        assertFalse(result.needInput());
    }

    @Test
    void stripsMarkdownFenceBeforeParsing() {
        CommandInterpretation result = parser.parse("""
                ```json
                {"action":"status","parameters":{},"confidence":1.0,"reply":"status {RESULT}","need_input":false}
                ```
                """);

        assertEquals(CommandAction.STATUS, result.action());
        assertEquals("status {RESULT}", result.reply());
    }

    @Test
    void parsesEscapedStringsInReplyAndParameters() {
        CommandInterpretation result = parser.parse("""
                {"action":"commit","parameters":{"message":"line \\"quoted\\""},"confidence":0.8,"reply":"reply\\nnext","need_input":true}
                """);

        assertEquals(CommandAction.COMMIT, result.action());
        assertEquals("line \"quoted\"", result.parameter("message"));
        assertEquals("reply\nnext", result.reply());
        assertTrue(result.needInput());
    }

    @Test
    void unknownActionFallsBackToUnknown() {
        CommandInterpretation result = parser.parse("""
                {"action":"nonsense","parameters":{},"confidence":0.2,"reply":"no","need_input":false}
                """);

        assertEquals(CommandAction.UNKNOWN, result.action());
    }

    @Test
    void missingFieldsUseSafeDefaults() {
        CommandInterpretation result = parser.parse("{}");

        assertEquals(CommandAction.UNKNOWN, result.action());
        assertEquals(0.0, result.confidence(), 0.0001);
        assertEquals("{RESULT}", result.reply());
        assertFalse(result.needInput());
    }

    @Test
    void parsesPrimitiveParameterValues() {
        CommandInterpretation result = parser.parse("""
                {"action":"checkout","parameters":{"name":123},"confidence":1,"reply":"go","need_input":false}
                """);

        assertEquals("123", result.parameter("name"));
    }

    @Test
    void parsesObjectParameterAsJsonString() {
        CommandInterpretation result = parser.parse("""
                {"action":"chat","parameters":{"meta":{"a":1}},"confidence":1,"reply":"go","need_input":false}
                """);

        assertEquals("{\"a\":1}", result.parameter("meta"));
    }

    @Test
    void invalidJsonFallsBackToUnknown() {
        CommandInterpretation result = parser.parse("not json");

        assertEquals(CommandAction.UNKNOWN, result.action());
        assertEquals("{RESULT}", result.reply());
    }
}
