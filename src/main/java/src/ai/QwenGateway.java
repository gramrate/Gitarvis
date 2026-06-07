package src.ai;

import src.config.AppConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class QwenGateway implements AiGateway {
    private final HttpClient httpClient;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final Duration requestTimeout;

    public QwenGateway(String baseUrl, String apiKey, String model, double temperature, Duration connectTimeout, Duration requestTimeout) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("ai.baseUrl is not set.");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("ai.model is not set.");
        }
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        this.endpoint = URI.create(trimTrailingSlash(baseUrl) + "/chat/completions");
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model;
        this.temperature = temperature;
        this.requestTimeout = requestTimeout;
    }

    public static QwenGateway fromConfig(AppConfig.AiConfig config) {
        return new QwenGateway(
                config.baseUrl(),
                config.apiKey(),
                config.model(),
                config.temperature(),
                Duration.ofSeconds(config.connectTimeoutSeconds()),
                Duration.ofSeconds(config.requestTimeoutSeconds())
        );
    }

    @Override
    public String complete(String prompt) throws IOException, InterruptedException {
        String body = """
                {
                  "model": "%s",
                  "messages": [
                    {
                      "role": "user",
                      "content": "%s"
                    }
                  ],
                  "temperature": %s
                }
                """.formatted(jsonEscape(model), jsonEscape(prompt), temperature);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));

        if (!apiKey.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }

        HttpRequest request = requestBuilder.build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("AI request failed with status " + response.statusCode() + ": " + response.body());
        }
        return extractContent(response.body());
    }

    private static String extractContent(String json) throws IOException {
        String marker = "\"content\"";
        int markerIndex = json.indexOf(marker);
        if (markerIndex < 0) {
            throw new IOException("AI response does not contain message content: " + json);
        }
        int colonIndex = json.indexOf(':', markerIndex + marker.length());
        int valueStart = json.indexOf('"', colonIndex + 1);
        if (colonIndex < 0 || valueStart < 0) {
            throw new IOException("Cannot parse AI response content: " + json);
        }
        return readJsonString(json, valueStart);
    }

    private static String readJsonString(String json, int quoteIndex) throws IOException {
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int i = quoteIndex + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                value.append(switch (c) {
                    case '"', '\\', '/' -> c;
                    case 'b' -> '\b';
                    case 'f' -> '\f';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> c;
                });
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return value.toString();
            } else {
                value.append(c);
            }
        }
        throw new IOException("Unterminated JSON string in AI response.");
    }

    private static String jsonEscape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String trimTrailingSlash(String value) {
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
