package src.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AppConfigTest {
    @Test
    void defaultsAreAppliedWhenMapIsEmpty() {
        AppConfig config = AppConfig.from(Map.of());

        assertEquals("http://localhost:11434/v1", config.ai().baseUrl());
        assertEquals("qwen2.5:3b", config.ai().model());
        assertEquals(0.1, config.ai().temperature(), 0.0001);
        assertEquals(160, config.ai().maxTokens());
        assertEquals("models/vosk-model-small-ru", config.vosk().modelPath());
    }

    @Test
    void explicitValuesOverrideDefaults() {
        AppConfig config = AppConfig.from(Map.of(
                "ai.baseUrl", "http://ai",
                "ai.apiKey", "secret",
                "ai.model", "model",
                "ai.temperature", "0.7",
                "ai.maxTokens", "42",
                "ai.connectTimeoutSeconds", "3",
                "ai.requestTimeoutSeconds", "4",
                "vosk.modelPath", "models/custom",
                "vosk.nativeLibraryPath", "/native"
        ));

        assertEquals("http://ai", config.ai().baseUrl());
        assertEquals("secret", config.ai().apiKey());
        assertEquals("model", config.ai().model());
        assertEquals(0.7, config.ai().temperature(), 0.0001);
        assertEquals(42, config.ai().maxTokens());
        assertEquals(3, config.ai().connectTimeoutSeconds());
        assertEquals(4, config.ai().requestTimeoutSeconds());
        assertEquals("models/custom", config.vosk().modelPath());
        assertEquals("/native", config.vosk().nativeLibraryPath());
    }
}
