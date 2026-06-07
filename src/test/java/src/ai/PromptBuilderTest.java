package src.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class PromptBuilderTest {
    private final PromptBuilder promptBuilder = new PromptBuilder();

    @Test
    void commandPromptContainsUserTextAndJsonContract() {
        String prompt = promptBuilder.commandInterpretationPrompt("покажи статус");

        assertTrue(prompt.contains("Сообщение пользователя: покажи статус"));
        assertTrue(prompt.contains("Всегда возвращай ТОЛЬКО валидный JSON"));
        assertTrue(prompt.contains("\"action\""));
        assertTrue(prompt.contains("\"parameters\""));
        assertTrue(prompt.contains("\"need_input\""));
    }

    @Test
    void commandPromptDocumentsGitActionsAndStateActions() {
        String prompt = promptBuilder.commandInterpretationPrompt("добавь и сохрани");

        assertTrue(prompt.contains("\"add_commit\""));
        assertTrue(prompt.contains("\"standby\""));
        assertTrue(prompt.contains("\"exit\""));
        assertTrue(prompt.contains("Если пользователь просит добавить изменения и сохранить без сообщения"));
    }

    @Test
    void commandPromptUsesMatveyIdentity() {
        String prompt = promptBuilder.commandInterpretationPrompt("привет");

        assertTrue(prompt.contains("Ты Матвей"));
    }
}
