package src.core;

import src.ai.AiGateway;
import src.ai.PromptBuilder;
import src.domain.CommandExecutor;
import src.domain.CommandParser;
import src.domain.model.CommandAction;
import src.domain.model.CommandInterpretation;
import src.domain.model.GitResult;
import src.input.InputSource;
import src.output.OutputSink;
import src.output.ResponseFormatter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class AssistantLoop {
    private enum AssistantState {
        WAITING,
        ACTIVE
    }

    private final InputSource inputSource;
    private final OutputSink outputSink;
    private final AiGateway aiGateway;
    private final PromptBuilder promptBuilder;
    private final CommandParser commandParser;
    private final CommandExecutor commandExecutor;
    private final ResponseFormatter responseFormatter;
    private CommandInterpretation pendingCommand;
    private AssistantState state = AssistantState.WAITING;

    public AssistantLoop(
            InputSource inputSource,
            OutputSink outputSink,
            AiGateway aiGateway,
            PromptBuilder promptBuilder,
            CommandParser commandParser,
            CommandExecutor commandExecutor,
            ResponseFormatter responseFormatter
    ) {
        this.inputSource = inputSource;
        this.outputSink = outputSink;
        this.aiGateway = aiGateway;
        this.promptBuilder = promptBuilder;
        this.commandParser = commandParser;
        this.commandExecutor = commandExecutor;
        this.responseFormatter = responseFormatter;
    }

    public void run() {
        outputSink.write("Я в режиме ожидания.");

        try {
            while (true) {
                Optional<String> input = inputSource.read();
                if (input.isEmpty()) {
                    return;
                }

                String userText = input.get().trim();
                if (isBlankCommand(userText)) {
                    if (state == AssistantState.ACTIVE) {
                        outputSink.write("Не расслышал команду. Повтори, пожалуйста.");
                    }
                    continue;
                }

                try {
                    StateResult stateResult = handleState(userText);
                    if (stateResult == StateResult.EXIT) {
                        outputSink.write("Завершаю работу.");
                        return;
                    }
                    if (stateResult == StateResult.CONSUMED) {
                        continue;
                    }
                    if (state == AssistantState.WAITING) {
                        continue;
                    }
                } catch (Exception e) {
                    outputSink.write("Ошибка " + e.getMessage());
                    continue;
                }

                if (isProgramExitCommand(userText)) {
                    outputSink.write("Выключаюсь. Если что — зови, я рядом с git.");
                    return;
                }

                try {
                    if (!handle(userText)) {
                        return;
                    }
                } catch (Exception e) {
                    outputSink.write("Ошибка " + e.getMessage());
                }
            }
        } finally {
            inputSource.close();
        }
    }

    private StateResult handleState(String userText) throws IOException, InterruptedException {
        String normalized = normalizeCommandText(userText);
        if (isProgramExitCommand(normalized)) {
            return StateResult.EXIT;
        }

        if (state == AssistantState.WAITING) {
            if (hasWakeName(normalized)) {
                state = AssistantState.ACTIVE;
                String commandAfterName = removeWakeName(userText).trim();
                outputSink.write("Я в активном режиме.");
                if (isBlankCommand(commandAfterName) || isWakeOnlyRemainder(commandAfterName)) {
                    outputSink.write("Я тут, слушаю вас.");
                    return StateResult.CONSUMED;
                }
                return handle(commandAfterName) ? StateResult.CONSUMED : StateResult.EXIT;
            }
            return StateResult.CONSUMED;
        }

        if (isStandbyCommand(normalized)) {
            pendingCommand = null;
            state = AssistantState.WAITING;
            outputSink.write("Я в режиме ожидания.");
            return StateResult.CONSUMED;
        }

        if (hasWakeName(normalized)) {
            return StateResult.PASS_TO_COMMANDS;
        }

        return StateResult.PASS_TO_COMMANDS;
    }

    private boolean handle(String userText) throws IOException, InterruptedException {
        if (pendingCommand != null) {
            if (hasBrokenText(userText)) {
                outputSink.write("Сообщение выглядит битым. Повтори, пожалуйста, я не хочу делать коммит с кашей в названии.");
                return true;
            }
            CommandInterpretation completedCommand = completePendingCommand(userText);
            pendingCommand = null;
            GitResult result = commandExecutor.execute(completedCommand);
            outputSink.write(responseFormatter.format(completedCommand.reply(), result, completedCommand.parameters()));
            return true;
        }

        if (tryHandleFastCommand(userText)) {
            return true;
        }

        String prompt = promptBuilder.commandInterpretationPrompt(userText);
        outputSink.write("Матвей думает");
        String aiResponse = aiGateway.complete(prompt);
        CommandInterpretation interpretation = commandParser.parse(aiResponse);

        if (interpretation.needInput()) {
            pendingCommand = interpretation;
            outputSink.write(interpretation.reply());
            return true;
        }

        if (requiresMissingInput(interpretation)) {
            pendingCommand = withMissingInput(interpretation);
            outputSink.write(pendingCommand.reply());
            return true;
        }

        if (interpretation.action() == CommandAction.STANDBY) {
            pendingCommand = null;
            state = AssistantState.WAITING;
            outputSink.write("Я в режиме ожидания.");
            return true;
        }

        if (interpretation.action() == CommandAction.EXIT) {
            outputSink.write(interpretation.reply() == null || interpretation.reply().isBlank() ? "Завершаю работу." : interpretation.reply());
            return false;
        }

        if (interpretation.action() == CommandAction.CHAT || interpretation.action() == CommandAction.UNKNOWN) {
            outputSink.write(cleanPlainReply(interpretation.reply()));
            return true;
        }

        GitResult result = commandExecutor.execute(interpretation);
        outputSink.write(responseFormatter.format(interpretation.reply(), result, interpretation.parameters()));
        return true;
    }

    private CommandInterpretation completePendingCommand(String userText) {
        Map<String, String> parameters = new LinkedHashMap<>(pendingCommand.parameters());
        String value = userText.trim();

        if (pendingCommand.action() == CommandAction.COMMIT || pendingCommand.action() == CommandAction.ADD_COMMIT) {
            parameters.put("message", value);
        } else if (pendingCommand.action() == CommandAction.BRANCH_CREATE || pendingCommand.action() == CommandAction.CHECKOUT) {
            parameters.put("name", value);
        }

        String reply = pendingCommand.reply();
        if (pendingCommand.action() == CommandAction.COMMIT) {
            reply = "Фиксирую! Коммит с сообщением «{MSG}» создан. {RESULT}";
        } else if (pendingCommand.action() == CommandAction.ADD_COMMIT) {
            reply = "Добавляю изменения и фиксирую с сообщением «{MSG}». {RESULT}";
        } else if (pendingCommand.action() == CommandAction.BRANCH_CREATE) {
            reply = "Ветка «{NAME}» создана, можно работать! {RESULT}";
        } else if (pendingCommand.action() == CommandAction.CHECKOUT) {
            reply = "Переключаюсь на ветку «{NAME}»... {RESULT}";
        }

        return new CommandInterpretation(pendingCommand.action(), parameters, pendingCommand.confidence(), reply, false);
    }

    private String cleanPlainReply(String reply) {
        if (reply == null || reply.isBlank()) {
            return "";
        }
        return reply.replace("{RESULT}", "")
                .replace("{MSG}", "")
                .replace("{NAME}", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean requiresMissingInput(CommandInterpretation interpretation) {
        return switch (interpretation.action()) {
            case COMMIT, ADD_COMMIT -> interpretation.parameter("message").isBlank();
            case BRANCH_CREATE, CHECKOUT -> interpretation.parameter("name").isBlank();
            default -> false;
        };
    }

    private CommandInterpretation withMissingInput(CommandInterpretation interpretation) {
        String reply = switch (interpretation.action()) {
            case COMMIT, ADD_COMMIT -> "Скажи сообщение для коммита — что пишем?";
            case BRANCH_CREATE -> "Как назвать новую ветку?";
            case CHECKOUT -> "На какую ветку переключиться?";
            default -> interpretation.reply();
        };
        return new CommandInterpretation(
                interpretation.action(),
                interpretation.parameters(),
                interpretation.confidence(),
                reply,
                true
        );
    }

    private boolean tryHandleFastCommand(String userText) throws IOException, InterruptedException {
        String normalized = normalizeCommandText(userText);
        if (isAddCommitPhrase(normalized)) {
            String message = extractCommitMessage(userText);
            CommandInterpretation addCommit = new CommandInterpretation(
                    CommandAction.ADD_COMMIT,
                    message.isBlank() ? Map.of() : Map.of("message", message),
                    1.0,
                    message.isBlank()
                            ? "Добавлю и сохраню. Только скажи сообщение для коммита — что пишем?"
                            : "Добавляю изменения и фиксирую с сообщением «{MSG}». {RESULT}",
                    message.isBlank()
            );

            if (addCommit.needInput()) {
                pendingCommand = addCommit;
                outputSink.write(addCommit.reply());
                return true;
            }

            GitResult result = commandExecutor.execute(addCommit);
            outputSink.write(responseFormatter.format(addCommit.reply(), result, addCommit.parameters()));
            return true;
        }

        if (isHelpPhrase(normalized)) {
            outputSink.write(helpText());
            return true;
        }

        if (isStatusPhrase(normalized)) {
            CommandInterpretation status = new CommandInterpretation(
                    CommandAction.STATUS,
                    Map.of(),
                    1.0,
                    "Смотрю, что у нас в рабочем дереве... {RESULT}",
                    false
            );
            GitResult result = commandExecutor.execute(status);
            outputSink.write(responseFormatter.format(status.reply(), result, status.parameters()));
            return true;
        }
        return false;
    }

    private boolean isStatusPhrase(String normalized) {
        return normalized.contains("статус")
                || normalized.contains("status")
                || (normalized.contains("есть") && normalized.contains("измен"))
                || normalized.contains("какие изменения")
                || normalized.contains("что измен")
                || normalized.contains("что поменялось")
                || normalized.contains("что поменяли")
                || normalized.contains("рабочем дереве");
    }

    private boolean isHelpPhrase(String normalized) {
        return normalized.contains("список команд")
                || normalized.contains("команды")
                || normalized.contains("что ты умеешь")
                || normalized.contains("помощь")
                || normalized.equals("help");
    }

    private boolean isAddCommitPhrase(String normalized) {
        boolean hasAdd = normalized.contains("добав") || normalized.contains("add");
        boolean hasCommit = normalized.contains("сохран")
                || normalized.contains("сохрани")
                || normalized.contains("созран")
                || normalized.contains("коммит")
                || normalized.contains("commit");
        return hasAdd && hasCommit;
    }

    private String extractCommitMessage(String userText) {
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

    private boolean isProgramExitCommand(String userText) {
        String normalized = normalizeCommandText(userText);
        return normalized.equals("exit")
                || normalized.equals("quit")
                || normalized.equals("завершение работы")
                || normalized.equals("заверши работу")
                || normalized.equals("завершить работу")
                || normalized.equals("закрой программу")
                || normalized.equals("выключи программу")
                || normalized.equals("выйди из программы");
    }

    private boolean isStandbyCommand(String normalized) {
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

    private boolean hasWakeName(String normalized) {
        for (String token : normalized.split("\\s+")) {
            if (isWakeNameToken(token)) {
                return true;
            }
        }
        return false;
    }

    private String removeWakeName(String text) {
        StringBuilder withoutName = new StringBuilder();
        for (String token : normalizeCommandText(text).split("\\s+")) {
            if (!isWakeNameToken(token)) {
                withoutName.append(token).append(' ');
            }
        }
        return withoutName.toString().trim();
    }

    private boolean isWakeOnlyRemainder(String text) {
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

    private boolean isWakeNoiseToken(String token) {
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

    private boolean isWakeNameToken(String token) {
        return token.startsWith("матве")
                || token.startsWith("мотве")
                || token.startsWith("мэтве")
                || token.startsWith("метве")
                || token.equals("мат")
                || token.equals("мэт")
                || token.equals("мет");
    }

    private boolean isBlankCommand(String userText) {
        return normalizeCommandText(userText).isBlank();
    }

    private boolean hasBrokenText(String userText) {
        return userText.indexOf('\uFFFD') >= 0;
    }

    private String helpText() {
        return """
                Могу вот что:
                init — создать репозиторий
                status — показать изменения
                add — добавить все изменения
                commit — сохранить с сообщением
                add_commit — добавить и сохранить с сообщением
                branch_create — создать ветку
                checkout — перейти на ветку
                push — отправить текущую ветку в origin
                спасибо / стоп — перейти в режим ожидания
                завершение работы — закрыть программу
                """.trim();
    }

    private String normalizeCommandText(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[.!?,…:;«»]", " ")
                .replace("—", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private enum StateResult {
        PASS_TO_COMMANDS,
        CONSUMED,
        EXIT
    }
}
