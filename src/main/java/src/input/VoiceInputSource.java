package src.input;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VoiceInputSource implements InputSource {
    private static final float SAMPLE_RATE = 16_000.0f;
    private static final int BUFFER_SIZE = 4_096;
    private static final Pattern TEXT_PATTERN = Pattern.compile("\"text\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
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

    private static String extractText(String json) {
        Matcher matcher = TEXT_PATTERN.matcher(json);
        if (!matcher.find()) {
            return "";
        }
        return unescapeJsonString(matcher.group(1));
    }

    private static String unescapeJsonString(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current != '\\' || i == value.length() - 1) {
                result.append(current);
                continue;
            }

            char escaped = value.charAt(++i);
            switch (escaped) {
                case '"', '\\', '/' -> result.append(escaped);
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> {
                    if (i + 4 <= value.length() - 1) {
                        String hex = value.substring(i + 1, i + 5);
                        result.append((char) Integer.parseInt(hex, 16));
                        i += 4;
                    }
                }
                default -> result.append(escaped);
            }
        }
        return result.toString().toLowerCase(Locale.ROOT);
    }
}
