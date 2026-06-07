package src.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class YamlConfigLoader {
    private static final String DEFAULT_RESOURCE = "application.yml";
    private static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)(?::([^}]*))?}");

    private YamlConfigLoader() {
    }

    public static AppConfig load() {
        String explicitPath = firstNotBlank(System.getProperty("gitarvis.config"), System.getenv("GITARVIS_CONFIG"));
        try {
            if (explicitPath != null) {
                return AppConfig.from(parse(Files.newBufferedReader(Path.of(explicitPath), StandardCharsets.UTF_8)));
            }

            Path localConfig = Path.of("config.yml");
            if (Files.exists(localConfig)) {
                return AppConfig.from(parse(Files.newBufferedReader(localConfig, StandardCharsets.UTF_8)));
            }

            InputStream resource = YamlConfigLoader.class.getClassLoader().getResourceAsStream(DEFAULT_RESOURCE);
            if (resource == null) {
                throw new IllegalStateException("Config file not found. Add config.yml or src/main/resources/application.yml.");
            }
            return AppConfig.from(parse(new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read config: " + e.getMessage(), e);
        }
    }

    private static Map<String, String> parse(BufferedReader reader) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        String currentSection = "";
        String line;
        while ((line = reader.readLine()) != null) {
            String withoutComment = stripComment(line);
            if (withoutComment.isBlank()) {
                continue;
            }

            int indent = countLeadingSpaces(withoutComment);
            String trimmed = withoutComment.trim();
            int colonIndex = trimmed.indexOf(':');
            if (colonIndex < 0) {
                continue;
            }

            String key = trimmed.substring(0, colonIndex).trim();
            String value = trimmed.substring(colonIndex + 1).trim();

            if (indent == 0 && value.isBlank()) {
                currentSection = key;
                continue;
            }

            String fullKey = indent == 0 || currentSection.isBlank() ? key : currentSection + "." + key;
            values.put(fullKey, resolveEnvironment(unquote(value)));
        }
        return values;
    }

    private static String stripComment(String line) {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (c == '#' && !inSingleQuote && !inDoubleQuote) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static int countLeadingSpaces(String value) {
        int count = 0;
        while (count < value.length() && value.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
                    .replace("\\\\", "\\");
        }
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String resolveEnvironment(String value) {
        Matcher matcher = ENV_PATTERN.matcher(value);
        StringBuilder resolved = new StringBuilder();
        while (matcher.find()) {
            String envValue = System.getenv(matcher.group(1));
            String fallback = matcher.group(2) == null ? "" : matcher.group(2);
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(envValue == null ? fallback : envValue));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    private static String firstNotBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }
}
