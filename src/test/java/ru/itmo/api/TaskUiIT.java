package ru.itmo.api;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import ru.itmo.ui.UiResponse;

@Tag("ui")
@DisplayName("Задачи через Swagger UI")
class TaskUiIT extends UiTestSupport {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("UI-01: создание и чтение задачи после перезагрузки страницы")
    void createsAndReadsTaskAfterReload() throws JsonProcessingException {
        String title = "Сдать лабораторную \"UI-тесты\"";
        UiResponse created = ui.createTask(title, 2, false);
        long id = taskId(created);
        JsonNode expected = task(id, title, 2, false);
        assertEquals(expected, created.json());

        ui.reload();
        UiResponse fetched = ui.readTask(id);
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

    @Test
    @DisplayName("UI-03: изменение всех полей с сохранением ID и результата")
    void replacesTaskAndPersistsChanges() throws JsonProcessingException {
        UiResponse created = ui.createTask("А", 1, false);
        long id = taskId(created);
        assertEquals(task(id, "А", 1, false), created.json());

        String title = "Сдать обновлённую лабораторную";
        JsonNode expected = task(id, title, 5, true);
        UiResponse updated = ui.replaceTask(id, title, 5, true);
        assertEquals(200, updated.status());
        assertEquals(expected, updated.json());

        ui.reload();
        UiResponse fetched = ui.readTask(id);
        assertEquals(200, fetched.status());
        assertEquals(expected, fetched.json());
        assertListContainsExactly(expected);
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
        assertEquals(422, invalidTitle.status());
        assertEquals("title", invalidTitle.json().path("field").asText());
        assertListContainsExactly(expected);

        UiResponse invalidPriority = ui.replaceTask(id, "Не должно сохраниться", 6, false);
        assertEquals(422, invalidPriority.status());
        assertEquals("priority", invalidPriority.json().path("field").asText());

        UiResponse fetched = ui.readTask(id);
        assertEquals(200, fetched.status());
        assertEquals(expected, fetched.json());
        assertListContainsExactly(expected);
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
