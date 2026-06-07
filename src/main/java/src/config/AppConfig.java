package src.config;

import java.util.Map;

public record AppConfig(AiConfig ai, VoskConfig vosk) {
    public static AppConfig from(Map<String, String> values) {
        return new AppConfig(
                new AiConfig(
                        value(values, "ai.baseUrl", "http://localhost:11434/v1"),
                        value(values, "ai.apiKey", ""),
                        value(values, "ai.model", "qwen2.5:3b"),
                        doubleValue(values, "ai.temperature", 0.1),
                        intValue(values, "ai.connectTimeoutSeconds", 20),
                        intValue(values, "ai.requestTimeoutSeconds", 60)
                ),
                new VoskConfig(
                        value(values, "vosk.modelPath", "models/vosk-model-small-ru-0.22"),
                        value(values, "vosk.nativeLibraryPath", "")
                )
        );
    }

    private static String value(Map<String, String> values, String key, String fallback) {
        String value = values.get(key);
        return value == null ? fallback : value;
    }

    private static int intValue(Map<String, String> values, String key, int fallback) {
        String value = value(values, key, "");
        return value.isBlank() ? fallback : Integer.parseInt(value);
    }

    private static double doubleValue(Map<String, String> values, String key, double fallback) {
        String value = value(values, key, "");
        return value.isBlank() ? fallback : Double.parseDouble(value);
    }

    public record AiConfig(
            String baseUrl,
            String apiKey,
            String model,
            double temperature,
            int connectTimeoutSeconds,
            int requestTimeoutSeconds
    ) {
    }

    public record VoskConfig(String modelPath, String nativeLibraryPath) {
    }
}
