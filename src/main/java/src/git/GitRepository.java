package src.git;

import src.domain.model.GitResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class GitRepository {
    private static final String BOT_COMMIT_SUFFIX = " (made by GJ)";

    private final Path workingDirectory;

    public GitRepository(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public GitResult init() throws IOException, InterruptedException {
        return git("init");
    }

    public GitResult status() throws IOException, InterruptedException {
        return git("status", "--short");
    }

    public GitResult log() throws IOException, InterruptedException {
        return git("log", "--oneline", "-10");
    }

    public GitResult diff(List<String> args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("diff"));
        command.addAll(args);
        return git(command);
    }

    public GitResult add(List<String> paths) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("add"));
        command.addAll(paths);
        return git(command);
    }

    public GitResult commit(String message) throws IOException, InterruptedException {
        return git("commit", "-m", withBotCommitSuffix(message));
    }

    public GitResult addAndCommit(String message) throws IOException, InterruptedException {
        GitResult addResult = add(List.of("."));
        if (!addResult.success()) {
            return addResult;
        }

        GitResult commitResult = commit(message);
        String output = joinOutput(addResult.output(), commitResult.output());
        return new GitResult(commitResult.success(), commitResult.exitCode(), output);
    }

    public GitResult push() throws IOException, InterruptedException {
        GitResult branchResult = git("branch", "--show-current");
        if (!branchResult.success() || branchResult.output().isBlank()) {
            return new GitResult(false, branchResult.exitCode(), "Не могу определить текущую ветку\n" + branchResult.output());
        }
        String currentBranch = branchResult.output().trim();
        return git("push", "origin", currentBranch);
    }

    public GitResult pull() throws IOException, InterruptedException {
        return git("pull");
    }

    public GitResult branch() throws IOException, InterruptedException {
        return git("branch", "--all");
    }

    public GitResult branchCreate(String branch) throws IOException, InterruptedException {
        return git("branch", branch);
    }

    public GitResult checkout(String branch) throws IOException, InterruptedException {
        return git("checkout", branch);
    }

    private GitResult git(String... args) throws IOException, InterruptedException {
        return git(List.of(args));
    }

    private GitResult git(List<String> args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(args);

        Process process = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true)
                .start();

        byte[] output = process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();
        return new GitResult(exitCode == 0, exitCode, new String(output, StandardCharsets.UTF_8));
    }

    private static String joinOutput(String first, String second) {
        if (first == null || first.isBlank()) {
            return second;
        }
        if (second == null || second.isBlank()) {
            return first;
        }
        return first.trim() + System.lineSeparator() + second.trim();
    }

    private static String withBotCommitSuffix(String message) {
        String trimmed = message == null ? "" : message.trim();
        if (trimmed.endsWith(BOT_COMMIT_SUFFIX)) {
            return trimmed;
        }
        return trimmed + BOT_COMMIT_SUFFIX;
    }
}
