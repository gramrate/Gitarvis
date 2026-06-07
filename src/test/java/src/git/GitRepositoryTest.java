package src.git;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import src.domain.model.GitResult;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GitRepositoryTest {
    @TempDir
    Path repoDir;

    @Test
    void initStatusAddCommitLogDiffBranchCheckoutAndPullAreCallable() throws Exception {
        GitRepository repository = new GitRepository(repoDir);

        assertTrue(repository.init().success());
        configureGitUser(repoDir);
        Files.writeString(repoDir.resolve("file.txt"), "one\n");

        GitResult status = repository.status();
        assertTrue(status.success());
        assertTrue(status.output().contains("file.txt"));

        assertTrue(repository.add(java.util.List.of(".")).success());
        GitResult commit = repository.commit("initial");
        assertTrue(commit.success(), commit.output());
        assertTrue(repository.log().output().contains("initial (made by GJ)"));

        Files.writeString(repoDir.resolve("file.txt"), "two\n");
        assertTrue(repository.diff(java.util.List.of()).output().contains("-one"));

        assertTrue(repository.branchCreate("feature").success());
        assertTrue(repository.checkout("feature").success());
        assertEquals("feature", currentBranch(repoDir));

        GitResult pull = repository.pull();
        assertEquals(false, pull.success());
    }

    @Test
    void addAndCommitAddsSuffixOnce() throws Exception {
        GitRepository repository = new GitRepository(repoDir);
        assertTrue(repository.init().success());
        configureGitUser(repoDir);
        Files.writeString(repoDir.resolve("file.txt"), "content\n");

        GitResult result = repository.addAndCommit("work (made by GJ)");

        assertTrue(result.success(), result.output());
        assertTrue(repository.log().output().contains("work (made by GJ)"));
    }

    @Test
    void logReturnsOnlyLastTenCommits() throws Exception {
        GitRepository repository = new GitRepository(repoDir);
        assertTrue(repository.init().success());
        configureGitUser(repoDir);

        for (int i = 1; i <= 12; i++) {
            Files.writeString(repoDir.resolve("file.txt"), "content " + i + "\n");
            assertTrue(repository.add(java.util.List.of(".")).success());
            assertTrue(repository.commit("commit " + i).success());
        }

        String output = repository.log().output();

        assertEquals(10, output.lines().count());
        assertTrue(output.contains("commit 12 (made by GJ)"));
        assertTrue(output.contains("commit 3 (made by GJ)"));
        assertEquals(false, output.contains("commit 2 (made by GJ)"));
    }

    @Test
    void pushFailsCleanlyWithoutBranchOrRemote() throws Exception {
        GitRepository repository = new GitRepository(repoDir);
        assertTrue(repository.init().success());

        GitResult result = repository.push();

        assertEquals(false, result.success());
    }

    private static void configureGitUser(Path repo) throws Exception {
        run(repo, "git", "config", "user.email", "test@example.com");
        run(repo, "git", "config", "user.name", "Test User");
    }

    private static String currentBranch(Path repo) throws Exception {
        return run(repo, "git", "branch", "--show-current").trim();
    }

    private static String run(Path repo, String... command) throws Exception {
        Process process = new ProcessBuilder(command)
                .directory(repo.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new AssertionError(String.join(" ", command) + " failed: " + output);
        }
        return output;
    }
}
