package src.output;

public final class TtsOutput implements OutputSink {
    @Override
    public void write(String text) {
        throw new UnsupportedOperationException("TTS output requires MaryTTS dependencies and voice setup.");
    }
}
