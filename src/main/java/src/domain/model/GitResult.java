package src.domain.model;

public record GitResult(boolean success, int exitCode, String output) {
    public static GitResult skipped(String message) {
        return new GitResult(false, -1, message);
    }

    public String resultText() {
        if (output == null || output.isBlank()) {
            return success ? "Команда выполнена успешно" : "Команда завершилась с кодом " + exitCode;
        }
        return output.trim();
    }
}
