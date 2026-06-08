package src.core;

import src.domain.model.CommandAction;
import src.domain.model.CommandInterpretation;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class AutoCommandUtils {
    private AutoCommandUtils() {
    }

    public static CommandAnalysis analyze(String userText) {
        String normalized = normalizeCommandText(userText);
        String textWithoutWakeName = removeWakeName(userText);
        return new CommandAnalysis(
                userText,
                normalized,
                normalized.isBlank(),
                isProgramExitCommand(normalized),
                isStandbyCommand(normalized),
                hasWakeName(normalized),
                textWithoutWakeName,
                isWakeOnlyRemainder(textWithoutWakeName),
                detectNormalized(userText, normalized)
        );
    }

    public static Optional<CommandInterpretation> detect(String userText) {
        String normalized = normalizeCommandText(userText);
        return detectNormalized(userText, normalized);
    }

    public static String normalizeCommandText(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[.!?,…:;«»]", " ")
                .replace("—", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static Optional<CommandInterpretation> detectNormalized(String userText, String normalized) {
        if (isAddCommitPhrase(normalized)) {
            String message = extractCommitMessage(userText);
            return Optional.of(new CommandInterpretation(
                    CommandAction.ADD_COMMIT,
                    message.isBlank() ? Map.of() : Map.of("message", message),
                    1.0,
                    message.isBlank()
                            ? "Добавлю и сохраню. Только скажи сообщение для коммита — что пишем?"
                            : "Добавляю изменения и фиксирую с сообщением «{MSG}». {RESULT}",
                    message.isBlank()
            ));
        }

        if (isHelpPhrase(normalized)) {
            return Optional.of(new CommandInterpretation(
                    CommandAction.HELP,
                    Map.of(),
                    1.0,
                    helpText(),
                    false
            ));
        }

        if (isStatusPhrase(normalized)) {
            return Optional.of(new CommandInterpretation(
                    CommandAction.STATUS,
                    Map.of(),
                    1.0,
                    "Смотрю, что у нас в рабочем дереве... {RESULT}",
                    false
            ));
        }

        if (isPullPhrase(normalized)) {
            return Optional.of(new CommandInterpretation(
                    CommandAction.PULL,
                    Map.of(),
                    1.0,
                    "Загружаю изменения из удалённого репозитория... {RESULT}",
                    false
            ));
        }

        return Optional.empty();
    }

    private static boolean isProgramExitCommand(String normalized) {
        return normalized.equals("exit")
                || normalized.equals("quit")
                || normalized.equals("завершение работы")
                || normalized.equals("заверши работу")
                || normalized.equals("завершить работу")
                || normalized.equals("закрой программу")
                || normalized.equals("выключи программу")
                || normalized.equals("выйди из программы");
    }

    private static boolean isStandbyCommand(String normalized) {
        return normalized.equals("спасибо")
                || normalized.startsWith("спасибо ")
                || normalized.equals("выход")
                || normalized.equals("выйти")
                || normalized.equals("выйди")
                || normalized.equals("выключись")
                || normalized.equals("выключайся")
                || normalized.equals("выключаемся")
                || normalized.equals("закройся")
                || normalized.equals("завершить")
                || normalized.equals("остановись")
                || normalized.equals("стоп")
                || normalized.equals("хватит")
                || normalized.equals("пока");
    }

    private static boolean hasWakeName(String normalized) {
        for (String token : normalized.split("\\s+")) {
            if (isWakeNameToken(token)) {
                return true;
            }
        }
        return false;
    }

    private static String removeWakeName(String text) {
        StringBuilder withoutName = new StringBuilder();
        for (String token : normalizeCommandText(text).split("\\s+")) {
            if (!isWakeNameToken(token)) {
                withoutName.append(token).append(' ');
            }
        }
        return withoutName.toString().trim();
    }

    private static boolean isWakeOnlyRemainder(String text) {
        String normalized = normalizeCommandText(text);
        if (normalized.isBlank()) {
            return true;
        }

        for (String token : normalized.split("\\s+")) {
            if (!isWakeNoiseToken(token)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWakeNoiseToken(String token) {
        return token.equals("а")
                || token.equals("э")
                || token.equals("ээ")
                || token.equals("эм")
                || token.equals("мм")
                || token.equals("ну")
                || token.equals("алло")
                || token.equals("слушай")
                || token.equals("пожалуйста");
    }

    private static boolean isWakeNameToken(String token) {
        return token.startsWith("матве")
                || token.startsWith("мотве")
                || token.startsWith("мэтве")
                || token.startsWith("метве")
                || token.equals("мат")
                || token.equals("мэт")
                || token.equals("мет");
    }

    private static boolean isStatusPhrase(String normalized) {
        return normalized.contains("статус")
                || normalized.contains("status")
                || (normalized.contains("есть") && normalized.contains("измен"))
                || normalized.contains("какие изменения")
                || normalized.contains("что измен")
                || normalized.contains("что поменялось")
                || normalized.contains("что поменяли")
                || normalized.contains("рабочем дереве");
    }

    private static boolean isHelpPhrase(String normalized) {
        return normalized.contains("список команд")
                || normalized.contains("команды")
                || normalized.contains("что ты умеешь")
                || normalized.contains("помощь")
                || normalized.equals("help");
    }

    private static boolean isPullPhrase(String normalized) {
        return normalized.contains("git pull")
                || normalized.equals("pull")
                || normalized.equals("пул")
                || normalized.contains("загрузить изменения")
                || normalized.contains("загрузи изменения")
                || normalized.contains("загружай изменения")
                || normalized.contains("скачай изменения")
                || normalized.contains("подтяни изменения")
                || normalized.contains("получи изменения")
                || normalized.contains("обнови изменения");
    }

    private static boolean isAddCommitPhrase(String normalized) {
        boolean hasAdd = normalized.contains("добав") || normalized.contains("add");
        boolean hasCommit = normalized.contains("сохран")
                || normalized.contains("сохрани")
                || normalized.contains("созран")
                || normalized.contains("коммит")
                || normalized.contains("commit");
        return hasAdd && hasCommit;
    }

    private static String extractCommitMessage(String userText) {
        String normalized = userText.toLowerCase(Locale.ROOT);
        String[] markers = {
                "с сообщением",
                "сообщением",
                "message",
                "месседжем"
        };

        int markerIndex = -1;
        String marker = "";
        for (String candidate : markers) {
            int index = normalized.indexOf(candidate);
            if (index >= 0 && (markerIndex < 0 || index < markerIndex)) {
                markerIndex = index;
                marker = candidate;
            }
        }

        if (markerIndex < 0) {
            return "";
        }

        return userText.substring(markerIndex + marker.length()).trim();
    }

    private static String helpText() {
        return """
                Могу вот что:
                init — создать репозиторий
                status — показать изменения
                add — добавить все изменения
                commit — сохранить с сообщением
                add_commit — добавить и сохранить с сообщением
                branch_create — создать ветку
                checkout — перейти на ветку
                pull — загрузить изменения из origin
                push — отправить текущую ветку в origin
                спасибо / стоп — перейти в режим ожидания
                завершение работы — закрыть программу
                """.trim();
    }

    public record CommandAnalysis(
            String original,
            String normalized,
            boolean blank,
            boolean programExit,
            boolean standby,
            boolean wakeName,
            String textWithoutWakeName,
            boolean wakeOnlyRemainder,
            Optional<CommandInterpretation> autoCommand
    ) {
    }
}
