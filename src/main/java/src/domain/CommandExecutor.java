package src.domain;

import src.domain.model.CommandAction;
import src.domain.model.CommandInterpretation;
import src.domain.model.GitResult;
import src.git.GitRepository;

import java.io.IOException;
import java.util.List;

public final class CommandExecutor {
    private final GitRepository gitRepository;

    public CommandExecutor(GitRepository gitRepository) {
        this.gitRepository = gitRepository;
    }

    public GitResult execute(CommandInterpretation command) throws IOException, InterruptedException {
        CommandAction action = command.action();
        if (command.needInput()) {
            return GitResult.skipped(command.reply());
        }

        return switch (action) {
            case INIT -> gitRepository.init();
            case STATUS -> gitRepository.status();
            case LOG -> gitRepository.log();
            case DIFF -> gitRepository.diff(List.of());
            case ADD -> gitRepository.add(List.of("."));
            case COMMIT -> command.parameter("message").isBlank()
                    ? GitResult.skipped("Нужно сообщение для коммита")
                    : gitRepository.commit(command.parameter("message"));
            case ADD_COMMIT -> command.parameter("message").isBlank()
                    ? GitResult.skipped("Нужно сообщение для коммита")
                    : gitRepository.addAndCommit(command.parameter("message"));
            case PUSH -> gitRepository.push();
            case PULL -> gitRepository.pull();
            case BRANCH -> gitRepository.branch();
            case BRANCH_CREATE -> command.parameter("name").isBlank()
                    ? GitResult.skipped("Нужно имя ветки")
                    : gitRepository.branchCreate(command.parameter("name"));
            case CHECKOUT -> command.parameter("name").isBlank()
                    ? GitResult.skipped("Нужно имя ветки")
                    : gitRepository.checkout(command.parameter("name"));
            case STANDBY, HELP, EXIT, CHAT, UNKNOWN -> GitResult.skipped("");
        };
    }
}
