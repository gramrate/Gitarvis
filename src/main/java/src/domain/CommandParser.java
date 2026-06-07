package src.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import src.domain.model.CommandAction;
import src.domain.model.CommandInterpretation;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class CommandParser {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CommandInterpretation parse(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(stripMarkdown(rawJson));
            return new CommandInterpretation(
                    parseAction(root.path("action").asText("")),
                    parseParameters(root.path("parameters")),
                    root.path("confidence").asDouble(0.0),
                    root.path("reply").asText(null),
                    root.path("need_input").asBoolean(false)
            );
        } catch (IOException | RuntimeException ignored) {
            return new CommandInterpretation(CommandAction.UNKNOWN, Map.of(), 0.0, null, false);
        }
    }

    private static CommandAction parseAction(String value) {
        if (value == null || value.isBlank()) {
            return CommandAction.UNKNOWN;
        }
        try {
            return CommandAction.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return CommandAction.UNKNOWN;
        }
    }

    private static Map<String, String> parseParameters(JsonNode parameters) {
        if (!parameters.isObject()) {
            return Map.of();
        }

        Map<String, String> values = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = parameters.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode value = field.getValue();
            if (value == null || value.isNull()) {
                values.put(field.getKey(), "");
            } else if (value.isTextual()) {
                values.put(field.getKey(), value.asText());
            } else {
                values.put(field.getKey(), value.toString());
            }
        }
        return values;
    }

    private static String stripMarkdown(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstNewline >= 0 && lastFence > firstNewline) {
            return trimmed.substring(firstNewline + 1, lastFence).trim();
        }
        return trimmed;
    }
}
