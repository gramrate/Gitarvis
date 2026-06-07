package src.domain.model;

import java.util.Map;

public record CommandInterpretation(
        CommandAction action,
        Map<String, String> parameters,
        double confidence,
        String reply,
        boolean needInput
) {
    public CommandInterpretation {
        action = action == null ? CommandAction.UNKNOWN : action;
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        reply = reply == null || reply.isBlank() ? "{RESULT}" : reply;
    }

    public String parameter(String name) {
        return parameters.getOrDefault(name, "");
    }
}
