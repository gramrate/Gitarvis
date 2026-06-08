package src.core;

import org.junit.jupiter.api.Test;
import src.domain.model.CommandAction;
import src.domain.model.CommandInterpretation;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AutoCommandUtilsTest {
    @Test
    void detectsHelpPhrases() {
        Optional<CommandInterpretation> command = AutoCommandUtils.detect("что ты умеешь");

        assertTrue(command.isPresent());
        assertEquals(CommandAction.HELP, command.get().action());
        assertTrue(command.get().reply().contains("add_commit"));
    }

    @Test
    void detectsStatusPhrases() {
        for (String phrase : java.util.List.of("покажи статус", "status", "какие изменения", "что поменялось")) {
            Optional<CommandInterpretation> command = AutoCommandUtils.detect(phrase);

            assertTrue(command.isPresent(), phrase);
            assertEquals(CommandAction.STATUS, command.get().action(), phrase);
        }
    }

    @Test
    void detectsPullPhrases() {
        for (String phrase : java.util.List.of("загрузить изменения", "загрузи изменения", "подтяни изменения", "git pull", "pull")) {
            Optional<CommandInterpretation> command = AutoCommandUtils.detect(phrase);

            assertTrue(command.isPresent(), phrase);
            assertEquals(CommandAction.PULL, command.get().action(), phrase);
            assertTrue(command.get().reply().contains("Загружаю изменения"));
        }
    }

    @Test
    void detectsAddCommitWithoutMessageAsNeedInput() {
        CommandInterpretation command = AutoCommandUtils.detect("добавь и сохрани").orElseThrow();

        assertEquals(CommandAction.ADD_COMMIT, command.action());
        assertTrue(command.needInput());
        assertEquals("", command.parameter("message"));
    }

    @Test
    void detectsAddCommitWithMessage() {
        CommandInterpretation command = AutoCommandUtils.detect("добавь и сохрани с сообщением готово").orElseThrow();

        assertEquals(CommandAction.ADD_COMMIT, command.action());
        assertFalse(command.needInput());
        assertEquals("готово", command.parameter("message"));
    }

    @Test
    void returnsEmptyForNonAutoCommand() {
        assertTrue(AutoCommandUtils.detect("расскажи как дела").isEmpty());
    }

    @Test
    void normalizesTextConsistently() {
        assertEquals("матвей покажи статус", AutoCommandUtils.normalizeCommandText("Матвей, покажи — статус!"));
    }

    @Test
    void analyzesWakeNamesAndRemovesThemFromCommand() {
        AutoCommandUtils.CommandAnalysis analysis = AutoCommandUtils.analyze("Мэт покажи статус");

        assertTrue(analysis.wakeName());
        assertEquals("покажи статус", analysis.textWithoutWakeName());
        assertFalse(analysis.wakeOnlyRemainder());
    }

    @Test
    void analyzesWakeOnlyNoise() {
        AutoCommandUtils.CommandAnalysis analysis = AutoCommandUtils.analyze("Матвей э ну пожалуйста");

        assertTrue(analysis.wakeName());
        assertEquals("э ну пожалуйста", analysis.textWithoutWakeName());
        assertTrue(analysis.wakeOnlyRemainder());
    }

    @Test
    void analyzesStandbyAndProgramExit() {
        assertTrue(AutoCommandUtils.analyze("спасибо матвей").standby());
        assertTrue(AutoCommandUtils.analyze("завершение работы").programExit());
        assertFalse(AutoCommandUtils.analyze("покажи статус").programExit());
    }

    @Test
    void analyzesBlankInput() {
        assertTrue(AutoCommandUtils.analyze(" ,  ").blank());
    }
}
