package src.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import src.config.AppConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class QwenGateway implements AiGateway {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final Duration requestTimeout;

    public QwenGateway(String baseUrl, String apiKey, String model, double temperature, int maxTokens, Duration connectTimeout, Duration requestTimeout) {
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
        this.maxTokens = maxTokens;
        this.requestTimeout = requestTimeout;
    }

    public static QwenGateway fromConfig(AppConfig.AiConfig config) {
        return new QwenGateway(
                config.baseUrl(),
                config.apiKey(),
                config.model(),
                config.temperature(),
                config.maxTokens(),
                Duration.ofSeconds(config.connectTimeoutSeconds()),
                Duration.ofSeconds(config.requestTimeoutSeconds())
        );
    }

    @Override
    public String complete(String prompt) throws IOException, InterruptedException {
        String body = buildRequestBody(prompt);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));

        if (!apiKey.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }

        HttpRequest request = requestBuilder.build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new IOException("AI не ответил за " + requestTimeout.toSeconds() + " секунд. Проверь requestTimeoutSeconds в активном конфиге.", e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("AI request failed with status " + response.statusCode() + ": " + response.body());
        }
        return extractContent(response.body());
    }

    private String buildRequestBody(String prompt) throws IOException {
        ObjectNode body = OBJECT_MAPPER.createObjectNode();
        body.put("model", model);
        ArrayNode messages = body.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        return OBJECT_MAPPER.writeValueAsString(body);
    }

    private static String extractContent(String json) throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(json);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (!content.isTextual()) {
            throw new IOException("AI response does not contain message content: " + json);
        }
        return content.asText();
    }

    private static String trimTrailingSlash(String value) {
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
