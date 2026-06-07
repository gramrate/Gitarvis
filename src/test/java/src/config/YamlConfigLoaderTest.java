package src.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class YamlConfigLoaderTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void clearConfigProperty() {
        System.clearProperty("gitarvis.config");
    }

    @Test
    void loadsExplicitConfigAndStripsCommentsAndQuotes() throws Exception {
        Path config = tempDir.resolve("config.yml");
        Files.writeString(config, """
                ai:
                  baseUrl: "http://localhost:9999/v1" # comment
                  apiKey: 'key'
                  model: "test-model"
                  temperature: 0.3
                  maxTokens: 99
                  connectTimeoutSeconds: 5
                  requestTimeoutSeconds: 6
                vosk:
                  modelPath: "models/test"
                  nativeLibraryPath: "/native/path"
                """);
        System.setProperty("gitarvis.config", config.toString());

        AppConfig loaded = YamlConfigLoader.load();

        assertEquals("http://localhost:9999/v1", loaded.ai().baseUrl());
        assertEquals("key", loaded.ai().apiKey());
        assertEquals("test-model", loaded.ai().model());
        assertEquals(0.3, loaded.ai().temperature(), 0.0001);
        assertEquals(99, loaded.ai().maxTokens());
        assertEquals(5, loaded.ai().connectTimeoutSeconds());
        assertEquals(6, loaded.ai().requestTimeoutSeconds());
        assertEquals("models/test", loaded.vosk().modelPath());
        assertEquals("/native/path", loaded.vosk().nativeLibraryPath());
    }

    @Test
    void resolvesEnvironmentFallbackSyntax() throws Exception {
        Path config = tempDir.resolve("config.yml");
        Files.writeString(config, """
                ai:
                  baseUrl: "${MISSING_GITARVIS_TEST_URL:http://fallback/v1}"
                  model: "${MISSING_GITARVIS_TEST_MODEL:model-from-fallback}"
                """);
        System.setProperty("gitarvis.config", config.toString());

        AppConfig loaded = YamlConfigLoader.load();

        assertEquals("http://fallback/v1", loaded.ai().baseUrl());
        assertEquals("model-from-fallback", loaded.ai().model());
    }

    @Test
    void snakeYamlParsesRealYamlTypesAndNestedMaps() throws Exception {
        Path config = tempDir.resolve("config.yml");
        Files.writeString(config, """
                ai:
                  baseUrl: http://typed/v1
                  temperature: 1
                  maxTokens: 64
                  nested:
                    ignored: true
                vosk:
                  modelPath: models/typed
                extra:
                  list:
                    - one
                    - two
                """);
        System.setProperty("gitarvis.config", config.toString());

        AppConfig loaded = YamlConfigLoader.load();

        assertEquals("http://typed/v1", loaded.ai().baseUrl());
        assertEquals(1.0, loaded.ai().temperature(), 0.0001);
        assertEquals(64, loaded.ai().maxTokens());
        assertEquals("models/typed", loaded.vosk().modelPath());
    }
}
