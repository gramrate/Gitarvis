package src.input;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import src.config.AppConfig;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

public final class VoiceInputSource implements InputSource {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final float SAMPLE_RATE = 16_000.0f;
    private static final int BUFFER_SIZE = 4_096;
    private static final String HEARD_PREFIX = "Vosk услышал: ";

    private final Path modelPath;
    private final Path nativeLibraryPath;
    private final Model model;
    private final Recognizer recognizer;
    private final TargetDataLine microphone;

    public VoiceInputSource(AppConfig.VoskConfig config) {
        this.modelPath = Path.of(config.modelPath());
        this.nativeLibraryPath = config.nativeLibraryPath() == null || config.nativeLibraryPath().isBlank()
                ? null
                : Path.of(config.nativeLibraryPath());

        if (nativeLibraryPath != null) {
            System.setProperty("jna.library.path", nativeLibraryPath.toString());
        }

        try {
            LibVosk.setLogLevel(LogLevel.WARNINGS);
            this.model = new Model(modelPath.toString());
            this.recognizer = new Recognizer(model, SAMPLE_RATE);
            this.microphone = openMicrophone();
            this.microphone.start();
        } catch (IOException | LineUnavailableException e) {
            throw new IllegalStateException("Cannot initialize Russian Vosk voice input: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<String> read() {
        byte[] buffer = new byte[BUFFER_SIZE];

        while (!Thread.currentThread().isInterrupted()) {
            int bytesRead = microphone.read(buffer, 0, buffer.length);
            if (bytesRead <= 0) {
                continue;
            }

            if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                String text = extractText(recognizer.getResult()).trim();
                if (!text.isBlank()) {
                    System.out.println(HEARD_PREFIX + text);
                    return Optional.of(text);
                }
            }
        }

        Thread.currentThread().interrupt();
        return Optional.empty();
    }

    @Override
    public void close() {
        microphone.stop();
        microphone.close();
        recognizer.close();
        model.close();
    }

    private static TargetDataLine openMicrophone() throws LineUnavailableException {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("Microphone does not support " + format);
        }

        TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(format);
        return line;
    }

    static String extractText(String json) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            return root.path("text").asText("").toLowerCase(Locale.ROOT);
        } catch (IOException | RuntimeException ignored) {
            return "";
        }
    }
}
