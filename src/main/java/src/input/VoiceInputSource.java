package src.input;

import src.config.AppConfig;

import java.nio.file.Path;
import java.util.Optional;

public final class VoiceInputSource implements InputSource {
    private final Path modelPath;
    private final Path nativeLibraryPath;

    public VoiceInputSource(AppConfig.VoskConfig config) {
        this.modelPath = Path.of(config.modelPath());
        this.nativeLibraryPath = config.nativeLibraryPath() == null || config.nativeLibraryPath().isBlank()
                ? null
                : Path.of(config.nativeLibraryPath());
    }

    @Override
    public Optional<String> read() {
        throw new UnsupportedOperationException(
                "Voice input requires Vosk setup. modelPath=" + modelPath + ", nativeLibraryPath=" + nativeLibraryPath
        );
    }
}
