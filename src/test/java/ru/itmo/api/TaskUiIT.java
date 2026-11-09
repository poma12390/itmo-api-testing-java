package ru.itmo.api;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Named.named;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.itmo.ui.TaskInput;
import ru.itmo.ui.UiResponse;

@Tag("ui")
@DisplayName("Задачи через Swagger UI")
@Timeout(value = 45, threadMode = Timeout.ThreadMode.SAME_THREAD)
class TaskUiIT extends UiTestSupport {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("UI-01: создание и чтение задачи после перезагрузки страницы")
    void createsAndReadsTaskAfterReload() throws JsonProcessingException {
        var input = new TaskInput("Сдать лабораторную \"UI-тесты\"", 2, false);
        UiResponse created = ui.createTask(input);
        long id = taskId(created);
        JsonNode expected = task(id, input);
        assertEquals(expected, created.json());

        UiResponse fetched = ui.reload().readTask(id);
        assertEquals(200, fetched.status());
        assertEquals(expected, fetched.json());
        assertListContainsExactly(expected);
    }

    @Test
    @DisplayName("UI-02: название из 100 символов и значения полей по умолчанию")
    void appliesDefaultsToTaskWithMaximumTitleLength() throws JsonProcessingException {
        String title = "я".repeat(100);
        UiResponse created = ui.createTask(title);
        long id = taskId(created);
        JsonNode expected = task(id, title, 3, false);
        assertEquals(expected, created.json());

        UiResponse fetched = ui.readTask(id);
        assertEquals(200, fetched.status());
        assertEquals(expected, fetched.json());
    }

    @ParameterizedTest(name = "{index}. {0}")
    @MethodSource("replacements")
    @DisplayName("UI-03: замена задачи с сохранением ID и результата")
    void replacesTaskAndPersistsChanges(TaskInput replacement, TaskInput original, TaskInput expectedState)
            throws JsonProcessingException {
        UiResponse created = ui.createTask(original);
        long id = taskId(created);
        assertEquals(task(id, original), created.json());

        JsonNode expected = task(id, expectedState);
        UiResponse updated = ui.replaceTask(id, replacement);
        assertEquals(200, updated.status());
        assertEquals(expected, updated.json());

        UiResponse fetched = ui.reload().readTask(id);
        assertEquals(200, fetched.status());
        assertEquals(expected, fetched.json());
        assertListContainsExactly(expected);
    }

    static Stream<Arguments> replacements() {
        var explicit = new TaskInput("Сдать обновлённую лабораторную", 5, true);
        var titleOnly = TaskInput.titleOnly("Сбросить приоритет и статус");
        return Stream.of(
                Arguments.of(named("Все поля заданы явно", explicit), new TaskInput("А", 1, false), explicit),
                Arguments.of(named("Пропущенные поля получают значения по умолчанию", titleOnly),
                        new TaskInput("А", 1, true), new TaskInput(titleOnly.title(), 3, false)));
    }

    @Test
    @DisplayName("UI-04: удаление, пустой список и ошибка при повторном обращении")
    void deletesTaskAndShowsNotFound() {
        long id = taskId(ui.createTask("Удалить после проверки"));
        assertEquals(200, ui.readTask(id).status());

        UiResponse deleted = ui.deleteTask(id);
        assertAll(
                () -> assertEquals(204, deleted.status()),
                () -> assertEquals("", deleted.body()));

        // Повторно используем тот же блок GET: ответ 200 должен смениться на 404.
        UiResponse missing = ui.readTask(id);
        assertEquals(404, missing.status());
        assertEquals("Task not found", missing.json().path("detail").asText());
        assertListContainsExactly();
        assertEquals(404, ui.deleteTask(id).status());
    }

    @Test
    @DisplayName("UI-05: невалидные POST и PUT показывают ошибку и не меняют данные")
    void rejectsInvalidInputWithoutChangingStoredTask() throws JsonProcessingException {
        UiResponse created = ui.createTask("Сохранить без изменений", 2, true);
        long id = taskId(created);
        JsonNode expected = task(id, "Сохранить без изменений", 2, true);
        assertEquals(expected, created.json());

        UiResponse invalidTitle = ui.createTask(" \t\n");
        assertValidationError(invalidTitle, "title");
        assertListContainsExactly(expected);

        UiResponse invalidPriority = ui.replaceTask(id, "Не должно сохраниться", 6, false);
        assertValidationError(invalidPriority, "priority");

        UiResponse fetched = ui.readTask(id);
        assertEquals(200, fetched.status());
        assertEquals(expected, fetched.json());
        assertListContainsExactly(expected);
    }

    @Test
    @DisplayName("Название из неразрывного пробела отклоняется")
    @Disabled("BUG-002: POST сохраняет название из U+00A0, см. docs/web-testing/bugs/BUG-002.md")
    void rejectsNonBreakingSpaceTitle() {
        UiResponse response = ui.createTask("\u00a0");
        assertValidationError(response, "title");
        assertListContainsExactly();
    }

    private void assertValidationError(UiResponse response, String field) {
        assertAll("Ошибка в Server response",
                () -> assertEquals(422, response.status(), response.body()),
                () -> assertEquals(field, response.json().path("field").asText()));
    }

    private long taskId(UiResponse created) {
        assertEquals(201, created.status(), created.body());
        JsonNode id = created.json().path("id");
        assertTrue(id.isIntegralNumber() && id.asLong() > 0, "Созданная задача имеет положительный ID");
        return id.asLong();
    }

    private JsonNode task(long id, String title, int priority, boolean completed)
            throws JsonProcessingException {
        return json.readTree(json.writeValueAsString(Map.of(
                "id", id, "title", title, "priority", priority, "completed", completed)));
    }

    private JsonNode task(long id, TaskInput value) throws JsonProcessingException {
        return task(id, value.title(), value.priority(), value.completed());
    }

    private void assertListContainsExactly(JsonNode... tasks) {
        UiResponse response = ui.listTasks();
        assertEquals(200, response.status());
        var expected = json.createArrayNode();
        for (JsonNode task : tasks) {
            expected.add(task);
        }
        assertEquals(expected, response.json());
    }
}
