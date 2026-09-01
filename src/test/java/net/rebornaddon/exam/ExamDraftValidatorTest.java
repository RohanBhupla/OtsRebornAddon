package net.rebornaddon.exam;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExamDraftValidatorTest {
    @Test
    public void acceptsAnyCorrectAnswerQuestion() {
        JsonObject draft = baseDraft();
        JsonObject question = question("any", 2);
        answer(question, "a", true);
        answer(question, "b", true);
        answer(question, "c", false);
        draft.getAsJsonArray("questions").add(question);
        assertTrue(ExamDraftValidator.validate(draft).isEmpty());
    }

    @Test
    public void rejectsSingleChoiceWithTwoCorrectAnswers() {
        JsonObject draft = baseDraft();
        JsonObject question = question("single", 1);
        answer(question, "a", true);
        answer(question, "b", true);
        draft.getAsJsonArray("questions").add(question);
        assertFalse(ExamDraftValidator.validate(draft).isEmpty());
    }

    @Test
    public void allowsPracticalOnlyExamWithoutQuestions() {
        JsonObject draft = baseDraft();
        JsonArray stages = new JsonArray();
        stages.add("practical");
        draft.add("stages", stages);
        assertTrue(ExamDraftValidator.validate(draft).isEmpty());
    }

    private static JsonObject baseDraft() {
        JsonObject draft = new JsonObject();
        draft.addProperty("title", "Chunin Selection Exam");
        draft.addProperty("rankId", "chunin");
        draft.addProperty("capacity", 32);
        JsonArray stages = new JsonArray();
        stages.add("written");
        draft.add("stages", stages);
        draft.add("questions", new JsonArray());
        JsonObject rules = new JsonObject();
        rules.add("allowedWeapons", new JsonArray());
        draft.add("tournamentRules", rules);
        draft.add("bracket", new JsonArray());
        return draft;
    }

    private static JsonObject question(String mode, int maximum) {
        JsonObject question = new JsonObject();
        question.addProperty("text", "Which answers are correct?");
        question.addProperty("selectionMode", mode);
        question.addProperty("maxSelections", maximum);
        question.add("answers", new JsonArray());
        return question;
    }

    private static void answer(JsonObject question, String id, boolean correct) {
        JsonObject answer = new JsonObject();
        answer.addProperty("id", id);
        answer.addProperty("text", "Answer " + id);
        answer.addProperty("correct", correct);
        question.getAsJsonArray("answers").add(answer);
    }
}
