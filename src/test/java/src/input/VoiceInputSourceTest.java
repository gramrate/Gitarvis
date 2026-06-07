package src.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class VoiceInputSourceTest {
    @Test
    void extractsTextFromVoskJsonWithJackson() {
        String text = VoiceInputSource.extractText("""
                {"text":"Матвей покажи \\"статус\\""}
                """);

        assertEquals("матвей покажи \"статус\"", text);
    }

    @Test
    void missingOrInvalidTextReturnsBlank() {
        assertEquals("", VoiceInputSource.extractText("{\"partial\":\"мат\"}"));
        assertEquals("", VoiceInputSource.extractText("not json"));
    }
}
