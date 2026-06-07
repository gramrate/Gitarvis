package src.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import src.ai.AiGateway;
import src.ai.PromptBuilder;
import src.domain.CommandExecutor;
import src.domain.CommandParser;
import src.git.GitRepository;
import src.input.InputSource;
import src.output.OutputSink;
import src.output.ResponseFormatter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AssistantLoopTest {
    @TempDir
    Path repoDir;

    @Test
    void waitingModeIgnoresCommandsWithoutWakeNameAndDoesNotCallAi() throws Exception {
        Harness harness = harness(List.of("покажи статус"), List.of());

        harness.run();

        assertEquals(List.of("Я в режиме ожидания."), harness.output.lines);
        assertEquals(0, harness.ai.prompts.size());
        assertTrue(harness.input.closed);
    }

    @Test
    void wakeNameOnlyActivatesWithoutCallingAi() throws Exception {
        Harness harness = harness(List.of("матвей"), List.of());

        harness.run();

        assertEquals(List.of(
                "Я в режиме ожидания.",
                "Я в активном режиме.",
                "Я тут, слушаю вас."
        ), harness.output.lines);
        assertEquals(0, harness.ai.prompts.size());
    }

    @Test
    void wakeNameWithNoiseOnlyDoesNotCallAi() throws Exception {
        Harness harness = harness(List.of("мэт э ну пожалуйста"), List.of());

        harness.run();

        assertEquals(List.of(
                "Я в режиме ожидания.",
                "Я в активном режиме.",
                "Я тут, слушаю вас."
        ), harness.output.lines);
        assertEquals(0, harness.ai.prompts.size());
    }

    @Test
    void wakeNameWithFastStatusCommandExecutesImmediatelyWithoutAi() throws Exception {
        initRepo();
        Files.writeString(repoDir.resolve("file.txt"), "content\n");
        Harness harness = harness(List.of("матвей покажи статус"), List.of());

        harness.run();

        assertEquals(0, harness.ai.prompts.size());
        assertEquals("Я в режиме ожидания.", harness.output.lines.get(0));
        assertEquals("Я в активном режиме.", harness.output.lines.get(1));
        assertTrue(harness.output.joined().contains("file.txt"));
    }

    @Test
    void activeModePassesNormalChatToAiAndStripsPlaceholders() throws Exception {
        Harness harness = harness(
                List.of("матвей", "расскажи"),
                List.of("""
                        {"action":"chat","parameters":{},"confidence":0.9,"reply":"Рассказываю. {RESULT}","need_input":false}
                        """)
        );

        harness.run();

        assertEquals(1, harness.ai.prompts.size());
        assertTrue(harness.ai.prompts.getFirst().contains("Сообщение пользователя: расскажи"));
        assertTrue(harness.output.lines.contains("Рассказываю."));
    }

    @Test
    void activeModeUnknownAlsoStripsPlaceholders() throws Exception {
        Harness harness = harness(
                List.of("матвей", "непонятная фраза"),
                List.of("""
                        {"action":"unknown","parameters":{},"confidence":0.1,"reply":"Не понял {RESULT} {MSG} {NAME}","need_input":false}
                        """)
        );

        harness.run();

        assertTrue(harness.output.lines.contains("Не понял"));
    }

    @Test
    void standbyCommandReturnsToWaitingAndFollowingCommandsAreIgnored() throws Exception {
        Harness harness = harness(List.of("матвей", "спасибо", "покажи статус"), List.of());

        harness.run();

        assertEquals(List.of(
                "Я в режиме ожидания.",
                "Я в активном режиме.",
                "Я тут, слушаю вас.",
                "Я в режиме ожидания."
        ), harness.output.lines);
        assertEquals(0, harness.ai.prompts.size());
    }

    @Test
    void programExitClosesLoopFromWaitingMode() throws Exception {
        Harness harness = harness(List.of("завершение работы", "матвей"), List.of());

        harness.run();

        assertEquals(List.of("Я в режиме ожидания.", "Завершаю работу."), harness.output.lines);
        assertTrue(harness.input.closed);
    }

    @Test
    void aiExitClosesProgramFromActiveMode() throws Exception {
        Harness harness = harness(
                List.of("матвей", "закрой программу пожалуйста"),
                List.of("""
                        {"action":"exit","parameters":{},"confidence":0.9,"reply":"Завершаю работу.","need_input":false}
                        """)
        );

        harness.run();

        assertTrue(harness.output.lines.contains("Завершаю работу."));
    }

    @Test
    void aiStandbyReturnsToWaitingFromActiveMode() throws Exception {
        Harness harness = harness(
                List.of("матвей", "отдохни пока", "покажи статус"),
                List.of("""
                        {"action":"standby","parameters":{},"confidence":0.8,"reply":"Ок","need_input":false}
                        """)
        );

        harness.run();

        assertTrue(harness.output.lines.contains("Я в режиме ожидания."));
        assertEquals(1, harness.ai.prompts.size());
    }

    @Test
    void aiCommitWithoutMessageForcesPendingInputEvenWhenNeedInputIsFalse() throws Exception {
        Harness harness = harness(
                List.of("матвей", "сделай коммит"),
                List.of("""
                        {"action":"commit","parameters":{},"confidence":0.9,"reply":"bad","need_input":false}
                        """)
        );

        harness.run();

        assertTrue(harness.output.lines.contains("Скажи сообщение для коммита — что пишем?"));
    }

    @Test
    void fastAddCommitWithoutMessageAsksThenCommitsNextPhraseWithBotSuffix() throws Exception {
        initRepo();
        configureGitUser(repoDir);
        Files.writeString(repoDir.resolve("file.txt"), "content\n");
        Harness harness = harness(List.of("матвей добавь и сохрани", "тестовый коммит"), List.of());

        harness.run();

        assertEquals(0, harness.ai.prompts.size());
        assertTrue(harness.output.lines.contains("Добавлю и сохраню. Только скажи сообщение для коммита — что пишем?"));
        assertTrue(git("log", "--oneline").contains("тестовый коммит (made by GJ)"));
    }

    @Test
    void helpFastCommandWorksInActiveModeWithoutAi() throws Exception {
        Harness harness = harness(List.of("матвей", "список команд"), List.of());

        harness.run();

        assertEquals(0, harness.ai.prompts.size());
        assertTrue(harness.output.joined().contains("add_commit — добавить и сохранить с сообщением"));
    }

    @Test
    void aiErrorsAreReportedAndLoopContinues() throws Exception {
        FakeAiGateway ai = new FakeAiGateway(List.of());
        ai.failNext = true;
        FakeInput input = new FakeInput(List.of("матвей", "расскажи", "спасибо"));
        CapturingOutput output = new CapturingOutput();
        AssistantLoop loop = loop(input, output, ai);

        loop.run();

        assertTrue(output.joined().contains("Ошибка AI failed"));
        assertTrue(output.lines.contains("Я в режиме ожидания."));
    }

    private Harness harness(List<String> input, List<String> aiResponses) {
        FakeInput fakeInput = new FakeInput(input);
        CapturingOutput output = new CapturingOutput();
        FakeAiGateway ai = new FakeAiGateway(aiResponses);
        return new Harness(fakeInput, output, ai, loop(fakeInput, output, ai));
    }

    private AssistantLoop loop(InputSource input, OutputSink output, AiGateway ai) {
        return new AssistantLoop(
                input,
                output,
                ai,
                new PromptBuilder(),
                new CommandParser(),
                new CommandExecutor(new GitRepository(repoDir)),
                new ResponseFormatter()
        );
    }

    private void initRepo() throws Exception {
        git("init");
    }

    private void configureGitUser(Path repo) throws Exception {
        run(repo, "git", "config", "user.email", "test@example.com");
        run(repo, "git", "config", "user.name", "Test User");
    }

    private String git(String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        return run(repoDir, command.toArray(String[]::new));
    }

    private static String run(Path repo, String... command) throws Exception {
        Process process = new ProcessBuilder(command)
                .directory(repo.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new AssertionError(String.join(" ", command) + " failed: " + output);
        }
        return output;
    }

    private record Harness(FakeInput input, CapturingOutput output, FakeAiGateway ai, AssistantLoop loop) {
        void run() {
            loop.run();
        }
    }

    private static final class FakeInput implements InputSource {
        private final Queue<String> values;
        private boolean closed;

        private FakeInput(List<String> values) {
            this.values = new ArrayDeque<>(values);
        }

        @Override
        public Optional<String> read() {
            return values.isEmpty() ? Optional.empty() : Optional.of(values.remove());
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class CapturingOutput implements OutputSink {
        private final List<String> lines = new ArrayList<>();

        @Override
        public void write(String text) {
            lines.add(text);
        }

        private String joined() {
            return String.join("\n", lines);
        }
    }

    private static final class FakeAiGateway implements AiGateway {
        private final Queue<String> responses;
        private final List<String> prompts = new ArrayList<>();
        private boolean failNext;

        private FakeAiGateway(List<String> responses) {
            this.responses = new ArrayDeque<>(responses);
        }

        @Override
        public String complete(String prompt) throws IOException {
            prompts.add(prompt);
            if (failNext) {
                failNext = false;
                throw new IOException("AI failed");
            }
            if (responses.isEmpty()) {
                throw new IOException("No fake AI response");
            }
            return responses.remove();
        }
    }
}
