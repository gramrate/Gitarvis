package src.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
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
                try (InputStream input = Files.newInputStream(Path.of(explicitPath))) {
                    return AppConfig.from(parse(input));
                }
            }

            Path localConfig = Path.of("config.yml");
            if (Files.exists(localConfig)) {
                try (InputStream input = Files.newInputStream(localConfig)) {
                    return AppConfig.from(parse(input));
                }
            }

            try (InputStream resource = YamlConfigLoader.class.getClassLoader().getResourceAsStream(DEFAULT_RESOURCE)) {
                if (resource == null) {
                    throw new IllegalStateException("Config file not found. Add config.yml or src/main/resources/application.yml.");
                }
                return AppConfig.from(parse(resource));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read config: " + e.getMessage(), e);
        }
    }

    private static Map<String, String> parse(InputStream input) {
        Object loaded = new Yaml().load(input);
        Map<String, String> values = new LinkedHashMap<>();
        if (loaded instanceof Map<?, ?> map) {
            flatten("", map, values);
        }
        return values;
    }

    private static void flatten(String prefix, Map<?, ?> source, Map<String, String> target) {
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            String key = Objects.toString(entry.getKey(), "");
            if (key.isBlank()) {
                continue;
            }
            String fullKey = prefix.isBlank() ? key : prefix + "." + key;
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> child) {
                flatten(fullKey, child, target);
            } else if (value != null) {
                target.put(fullKey, resolveEnvironment(Objects.toString(value)));
            }
        }
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
