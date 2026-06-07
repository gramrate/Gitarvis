package src.domain;

import src.domain.model.CommandAction;
import src.domain.model.CommandInterpretation;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class CommandParser {
    public CommandInterpretation parse(String rawJson) {
        String json = stripMarkdown(rawJson);
        CommandAction action = parseAction(extractString(json, "action"));
        Map<String, String> parameters = extractStringObject(json, "parameters");
        double confidence = extractDouble(json, "confidence", 0.0);
        String reply = extractString(json, "reply");
        boolean needInput = extractBoolean(json, "need_input", false);
        return new CommandInterpretation(action, parameters, confidence, reply, needInput);
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

    private static String extractString(String json, String key) {
        int keyIndex = json.indexOf('"' + key + '"');
        if (keyIndex < 0) {
            return null;
        }
        int colonIndex = json.indexOf(':', keyIndex);
        int valueStart = json.indexOf('"', colonIndex + 1);
        if (colonIndex < 0 || valueStart < 0) {
            return null;
        }
        return readJsonString(json, valueStart);
    }

    private static Map<String, String> extractStringObject(String json, String key) {
        int keyIndex = json.indexOf('"' + key + '"');
        if (keyIndex < 0) {
            return Map.of();
        }
        int colonIndex = json.indexOf(':', keyIndex);
        int objectStart = json.indexOf('{', colonIndex + 1);
        int objectEnd = findMatching(json, objectStart, '{', '}');
        if (colonIndex < 0 || objectStart < 0 || objectEnd < 0) {
            return Map.of();
        }

        Map<String, String> values = new LinkedHashMap<>();
        int i = objectStart + 1;
        while (i < objectEnd) {
            if (json.charAt(i) != '"') {
                i++;
                continue;
            }
            String parameterKey = readJsonString(json, i);
            i = skipString(json, i) + 1;
            int parameterColon = json.indexOf(':', i);
            if (parameterColon < 0 || parameterColon >= objectEnd) {
                break;
            }
            int valueStart = nextNonWhitespace(json, parameterColon + 1);
            if (valueStart < objectEnd && json.charAt(valueStart) == '"') {
                values.put(parameterKey, readJsonString(json, valueStart));
                i = skipString(json, valueStart) + 1;
            } else {
                int valueEnd = findPrimitiveEnd(json, valueStart, objectEnd);
                values.put(parameterKey, json.substring(valueStart, valueEnd).trim());
                i = valueEnd;
            }
        }
        return values;
    }

    private static double extractDouble(String json, String key, double fallback) {
        String raw = extractPrimitive(json, key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean extractBoolean(String json, String key, boolean fallback) {
        String raw = extractPrimitive(json, key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> fallback;
        };
    }

    private static String extractPrimitive(String json, String key) {
        int keyIndex = json.indexOf('"' + key + '"');
        if (keyIndex < 0) {
            return null;
        }
        int colonIndex = json.indexOf(':', keyIndex);
        if (colonIndex < 0) {
            return null;
        }
        int start = nextNonWhitespace(json, colonIndex + 1);
        int end = findPrimitiveEnd(json, start, json.length());
        return json.substring(start, end).trim();
    }

    private static int findMatching(String json, int start, char open, char close) {
        if (start < 0) {
            return -1;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                inString = !inString;
            } else if (!inString && c == open) {
                depth++;
            } else if (!inString && c == close) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String readJsonString(String json, int quoteIndex) {
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
        return value.toString();
    }

    private static int skipString(String json, int quoteIndex) {
        boolean escaped = false;
        for (int i = quoteIndex + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return i;
            }
        }
        return json.length() - 1;
    }

    private static int nextNonWhitespace(String json, int start) {
        int i = start;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int findPrimitiveEnd(String json, int start, int limit) {
        int i = start;
        while (i < limit && json.charAt(i) != ',' && json.charAt(i) != '}' && json.charAt(i) != '\n') {
            i++;
        }
        return i;
    }
}
