package src.output;

import src.domain.model.GitResult;

import java.util.Map;

public final class ResponseFormatter {
    public String format(String replyTemplate, GitResult result) {
        return format(replyTemplate, result, Map.of());
    }

    public String format(String replyTemplate, GitResult result, Map<String, String> parameters) {
        String template = replyTemplate == null || replyTemplate.isBlank() ? "{RESULT}" : replyTemplate;
        template = applyParameters(template, parameters);
        String resultText = result.resultText();
        if (template.contains("{RESULT}")) {
            return template.replace("{RESULT}", resultText);
        }
        return template + System.lineSeparator() + resultText;
    }

    private String applyParameters(String template, Map<String, String> parameters) {
        String formatted = template;
        formatted = formatted.replace("{MSG}", parameters.getOrDefault("message", ""));
        formatted = formatted.replace("{NAME}", parameters.getOrDefault("name", ""));
        return formatted;
    }
}
