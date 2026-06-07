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
                AutoCommandUtils.CommandAnalysis analysis = AutoCommandUtils.analyze(userText);
                if (analysis.blank()) {
                    if (state == AssistantState.ACTIVE) {
                        outputSink.write("Не расслышал команду. Повтори, пожалуйста.");
                    }
                    continue;
                }

                try {
                    StateResult stateResult = handleState(analysis);
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

                try {
                    if (!handle(userText, analysis)) {
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

    private StateResult handleState(AutoCommandUtils.CommandAnalysis analysis) throws IOException, InterruptedException {
        if (analysis.programExit()) {
            return StateResult.EXIT;
        }

        if (state == AssistantState.WAITING) {
            if (analysis.wakeName()) {
                state = AssistantState.ACTIVE;
                String commandAfterName = analysis.textWithoutWakeName().trim();
                AutoCommandUtils.CommandAnalysis commandAnalysis = AutoCommandUtils.analyze(commandAfterName);
                outputSink.write("Я в активном режиме.");
                if (commandAnalysis.blank() || analysis.wakeOnlyRemainder()) {
                    outputSink.write("Я тут, слушаю вас.");
                    return StateResult.CONSUMED;
                }
                return handle(commandAfterName, commandAnalysis) ? StateResult.CONSUMED : StateResult.EXIT;
            }
            return StateResult.CONSUMED;
        }

        if (analysis.standby()) {
            pendingCommand = null;
            state = AssistantState.WAITING;
            outputSink.write("Я в режиме ожидания.");
            return StateResult.CONSUMED;
        }

        if (analysis.wakeName()) {
            return StateResult.PASS_TO_COMMANDS;
        }

        return StateResult.PASS_TO_COMMANDS;
    }

    private boolean handle(String userText, AutoCommandUtils.CommandAnalysis analysis) throws IOException, InterruptedException {
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

        if (tryHandleAutoCommand(analysis.autoCommand())) {
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

    private boolean tryHandleAutoCommand(Optional<CommandInterpretation> autoCommand) throws IOException, InterruptedException {
        if (autoCommand.isEmpty()) {
            return false;
        }

        CommandInterpretation command = autoCommand.get();
        if (command.needInput()) {
            pendingCommand = command;
            outputSink.write(command.reply());
            return true;
        }
        if (command.action() == CommandAction.HELP) {
            outputSink.write(command.reply());
            return true;
        }

        GitResult result = commandExecutor.execute(command);
        outputSink.write(responseFormatter.format(command.reply(), result, command.parameters()));
        return true;
    }

    private boolean hasBrokenText(String userText) {
        return userText.indexOf('\uFFFD') >= 0;
    }

    private enum StateResult {
        PASS_TO_COMMANDS,
        CONSUMED,
        EXIT
    }
}
