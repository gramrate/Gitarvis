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
    private final InputSource inputSource;
    private final OutputSink outputSink;
    private final AiGateway aiGateway;
    private final PromptBuilder promptBuilder;
    private final CommandParser commandParser;
    private final CommandExecutor commandExecutor;
    private final ResponseFormatter responseFormatter;
    private CommandInterpretation pendingCommand;

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
        outputSink.write("Gitarvis ready. Type a git request or 'exit'.");

        while (true) {
            Optional<String> input = inputSource.read();
            if (input.isEmpty()) {
                return;
            }

            String userText = input.get().trim();
            if (userText.isEmpty()) {
                continue;
            }
            if ("exit".equalsIgnoreCase(userText) || "quit".equalsIgnoreCase(userText)) {
                outputSink.write("Bye.");
                return;
            }

            try {
                handle(userText);
            } catch (Exception e) {
                outputSink.write("Error: " + e.getMessage());
            }
        }
    }

    private void handle(String userText) throws IOException, InterruptedException {
        if (pendingCommand != null) {
            CommandInterpretation completedCommand = completePendingCommand(userText);
            pendingCommand = null;
            GitResult result = commandExecutor.execute(completedCommand);
            outputSink.write(responseFormatter.format(completedCommand.reply(), result, completedCommand.parameters()));
            return;
        }

        String prompt = promptBuilder.commandInterpretationPrompt(userText);
        String aiResponse = aiGateway.complete(prompt);
        CommandInterpretation interpretation = commandParser.parse(aiResponse);

        if (interpretation.needInput()) {
            pendingCommand = interpretation;
            outputSink.write(interpretation.reply());
            return;
        }

        if (interpretation.action() == CommandAction.CHAT || interpretation.action() == CommandAction.UNKNOWN) {
            outputSink.write(interpretation.reply());
            return;
        }

        GitResult result = commandExecutor.execute(interpretation);
        outputSink.write(responseFormatter.format(interpretation.reply(), result, interpretation.parameters()));
    }

    private CommandInterpretation completePendingCommand(String userText) {
        Map<String, String> parameters = new LinkedHashMap<>(pendingCommand.parameters());
        String value = userText.trim();

        if (pendingCommand.action() == CommandAction.COMMIT) {
            parameters.put("message", value);
        } else if (pendingCommand.action() == CommandAction.BRANCH_CREATE || pendingCommand.action() == CommandAction.CHECKOUT) {
            parameters.put("name", value);
        }

        String reply = pendingCommand.reply();
        if (pendingCommand.action() == CommandAction.COMMIT) {
            reply = "Фиксирую! Коммит с сообщением «{MSG}» создан. {RESULT}";
        } else if (pendingCommand.action() == CommandAction.BRANCH_CREATE) {
            reply = "Ветка «{NAME}» создана, можно работать! {RESULT}";
        } else if (pendingCommand.action() == CommandAction.CHECKOUT) {
            reply = "Переключаюсь на ветку «{NAME}»... {RESULT}";
        }

        return new CommandInterpretation(pendingCommand.action(), parameters, pendingCommand.confidence(), reply, false);
    }
}
