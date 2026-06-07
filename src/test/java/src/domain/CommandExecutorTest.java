package src.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import src.domain.model.CommandAction;
import src.domain.model.CommandInterpretation;
import src.domain.model.GitResult;
import src.git.GitRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CommandExecutorTest {
    @TempDir
    Path repoDir;

    @Test
    void skipsCommandsThatStillNeedInput() throws Exception {
        CommandExecutor executor = new CommandExecutor(new GitRepository(repoDir));
        CommandInterpretation command = new CommandInterpretation(CommandAction.COMMIT, Map.of(), 1, "need msg", true);

        GitResult result = executor.execute(command);

        assertEquals(false, result.success());
        assertEquals("need msg", result.output());
    }

    @Test
    void validatesMissingCommitMessage() throws Exception {
        CommandExecutor executor = new CommandExecutor(new GitRepository(repoDir));

        GitResult result = executor.execute(new CommandInterpretation(CommandAction.COMMIT, Map.of(), 1, "reply", false));

        assertEquals(false, result.success());
        assertEquals("Нужно сообщение для коммита", result.output());
    }

    @Test
    void executesInitStatusAddAndCommitActions() throws Exception {
        CommandExecutor executor = new CommandExecutor(new GitRepository(repoDir));

        assertTrue(executor.execute(command(CommandAction.INIT)).success());
        configureGitUser(repoDir);
        Files.writeString(repoDir.resolve("file.txt"), "content\n");
        assertTrue(executor.execute(command(CommandAction.ADD)).success());

        GitResult commit = executor.execute(new CommandInterpretation(
                CommandAction.COMMIT,
                Map.of("message", "executor"),
                1,
                "ok {RESULT}",
                false
        ));

        assertTrue(commit.success(), commit.output());
        assertTrue(executor.execute(command(CommandAction.STATUS)).success());
        assertTrue(executor.execute(command(CommandAction.LOG)).success());

        Files.writeString(repoDir.resolve("file.txt"), "changed\n");
        assertTrue(executor.execute(command(CommandAction.DIFF)).success());
    }

    @Test
    void executesBranchAndCheckoutActions() throws Exception {
        CommandExecutor executor = new CommandExecutor(new GitRepository(repoDir));

        assertTrue(executor.execute(command(CommandAction.INIT)).success());
        configureGitUser(repoDir);
        Files.writeString(repoDir.resolve("file.txt"), "content\n");
        assertTrue(executor.execute(command(CommandAction.ADD)).success());
        assertTrue(executor.execute(new CommandInterpretation(CommandAction.COMMIT, Map.of("message", "base"), 1, "ok", false)).success());
        GitResult missingBranchName = executor.execute(command(CommandAction.BRANCH_CREATE));
        GitResult missingCheckoutName = executor.execute(command(CommandAction.CHECKOUT));
        assertEquals("Нужно имя ветки", missingBranchName.output());
        assertEquals("Нужно имя ветки", missingCheckoutName.output());

        assertTrue(executor.execute(new CommandInterpretation(CommandAction.BRANCH_CREATE, Map.of("name", "feature"), 1, "ok", false)).success());
        assertTrue(executor.execute(new CommandInterpretation(CommandAction.CHECKOUT, Map.of("name", "feature"), 1, "ok", false)).success());
        assertTrue(executor.execute(command(CommandAction.BRANCH)).success());
    }

    @Test
    void pullAndPushReturnGitResults() throws Exception {
        CommandExecutor executor = new CommandExecutor(new GitRepository(repoDir));

        assertTrue(executor.execute(command(CommandAction.INIT)).success());

        assertEquals(false, executor.execute(command(CommandAction.PULL)).success());
        assertEquals(false, executor.execute(command(CommandAction.PUSH)).success());
    }

    @Test
    void nonGitActionsAreSkipped() throws Exception {
        CommandExecutor executor = new CommandExecutor(new GitRepository(repoDir));

        for (CommandAction action : java.util.List.of(CommandAction.CHAT, CommandAction.UNKNOWN, CommandAction.STANDBY, CommandAction.EXIT, CommandAction.HELP)) {
            GitResult result = executor.execute(command(action));
            assertEquals(false, result.success(), action.name());
            assertEquals("", result.output(), action.name());
        }
    }

    private static CommandInterpretation command(CommandAction action) {
        return new CommandInterpretation(action, Map.of(), 1, "reply", false);
    }

    private static void configureGitUser(Path repo) throws Exception {
        run(repo, "git", "config", "user.email", "test@example.com");
        run(repo, "git", "config", "user.name", "Test User");
    }

    private static void run(Path repo, String... command) throws Exception {
        Process process = new ProcessBuilder(command)
                .directory(repo.toFile())
                .redirectErrorStream(true)
                .start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new AssertionError(String.join(" ", command) + " failed");
        }
    }
}
