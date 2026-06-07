package src.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import src.config.AppConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class QwenGatewayTest {
    private HttpServer server;
    private final List<CapturedRequest> requests = new ArrayList<>();

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsOpenAiCompatibleRequestAndReturnsContent() throws Exception {
        startServer(200, """
                {"choices":[{"message":{"content":"{\\"action\\":\\"chat\\",\\"reply\\":\\"ok\\"}"}}]}
                """);
        QwenGateway gateway = gateway("secret");

        String result = gateway.complete("hello \"quoted\"\nnext");

        assertEquals("{\"action\":\"chat\",\"reply\":\"ok\"}", result);
        assertEquals(1, requests.size());
        CapturedRequest request = requests.getFirst();
        assertEquals("/v1/chat/completions", request.path());
        assertEquals("Bearer secret", request.authorization());
        assertTrue(request.body().contains("\"model\":\"qwen-test\""));
        assertTrue(request.body().contains("hello \\\"quoted\\\"\\nnext"));
    }

    @Test
    void omitsAuthorizationHeaderWhenApiKeyIsBlank() throws Exception {
        startServer(200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}");

        assertEquals("ok", gateway("").complete("prompt"));

        assertEquals(null, requests.getFirst().authorization());
    }

    @Test
    void throwsIOExceptionOnNonSuccessStatus() throws Exception {
        startServer(500, "bad");

        IOException error = assertThrows(IOException.class, () -> gateway("").complete("prompt"));

        assertTrue(error.getMessage().contains("AI request failed with status 500"));
    }

    @Test
    void throwsIOExceptionWhenContentIsMissing() throws Exception {
        startServer(200, "{\"choices\":[]}");

        IOException error = assertThrows(IOException.class, () -> gateway("").complete("prompt"));

        assertTrue(error.getMessage().contains("does not contain message content"));
    }

    @Test
    void constructorRejectsMissingBaseUrlAndModel() {
        assertThrows(IllegalArgumentException.class, () -> new QwenGateway("", "", "model", 0, 1, Duration.ofSeconds(1), Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new QwenGateway("http://localhost", "", "", 0, 1, Duration.ofSeconds(1), Duration.ofSeconds(1)));
    }

    @Test
    void fromConfigBuildsUsableGateway() throws Exception {
        startServer(200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}");
        AppConfig.AiConfig config = new AppConfig.AiConfig(baseUrl(), "", "qwen-test", 0.1, 10, 2, 5);

        QwenGateway gateway = QwenGateway.fromConfig(config);

        assertEquals("ok", gateway.complete("prompt"));
    }

    private QwenGateway gateway(String apiKey) {
        return new QwenGateway(baseUrl(), apiKey, "qwen-test", 0.2, 123, Duration.ofSeconds(2), Duration.ofSeconds(5));
    }

    private String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort() + "/v1/";
    }

    private void startServer(int status, String responseBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requests.add(capture(exchange));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    private static CapturedRequest capture(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return new CapturedRequest(
                exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                body
        );
    }

    private record CapturedRequest(String path, String authorization, String body) {
    }
}
