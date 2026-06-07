package src;

import src.ai.AiGateway;
import src.ai.PromptBuilder;
import src.ai.QwenGateway;
import src.config.AppConfig;
import src.config.YamlConfigLoader;
import src.core.AssistantLoop;
import src.domain.CommandExecutor;
import src.domain.CommandParser;
import src.git.GitRepository;
import src.input.InputSource;
import src.input.TextInputSource;
import src.output.ConsolePrinter;
import src.output.OutputSink;
import src.output.ResponseFormatter;

import java.nio.file.Path;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        AppConfig config = YamlConfigLoader.load();
        Path repositoryPath = Path.of(System.getProperty("user.dir"));

        InputSource input = new TextInputSource(System.in);
        OutputSink output = new ConsolePrinter(System.out);
        AiGateway aiGateway = QwenGateway.fromConfig(config.ai());

        AssistantLoop loop = new AssistantLoop(
                input,
                output,
                aiGateway,
                new PromptBuilder(),
                new CommandParser(),
                new CommandExecutor(new GitRepository(repositoryPath)),
                new ResponseFormatter()
        );

        loop.run();
    }
}
