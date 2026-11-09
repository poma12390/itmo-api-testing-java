package ru.itmo.ui.pages;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;
import java.util.Map;
import ru.itmo.ui.UiResponse;

public final class SwaggerUiPage {
    private final Page page;
    private final ObjectMapper json = new ObjectMapper();

    public SwaggerUiPage(Page page) {
        this.page = page;
    }

    public SwaggerUiPage open() {
        page.navigate("/swagger-ui/index.html");
        waitUntilLoaded();
        return this;
    }

    public SwaggerUiPage reload() {
        page.reload();
        waitUntilLoaded();
        return this;
    }

    public UiResponse createTask(String title) {
        return operation("create").body(serialize(Map.of("title", title))).execute();
    }

    public UiResponse createTask(String title, int priority, boolean completed) {
        return operation("create").body(taskBody(title, priority, completed)).execute();
    }

    public UiResponse readTask(long id) {
        return operation("find").taskId(id).execute();
    }

    public UiResponse replaceTask(long id, String title, int priority, boolean completed) {
        return operation("replace").taskId(id).body(taskBody(title, priority, completed)).execute();
    }

    public UiResponse deleteTask(long id) {
        return operation("delete").taskId(id).execute();
    }

    public UiResponse listTasks() {
        return operation("list").execute();
    }

    private void waitUntilLoaded() {
        assertThat(page.locator(".opblock")).hasCount(5);
        assertThat(page.locator(".info .title")).containsText("Tasks API");
    }

    private OperationPanel operation(String operationId) {
        return new OperationPanel(page.locator("#operations-task-controller-" + operationId)).edit();
    }

    private String taskBody(String title, int priority, boolean completed) {
        return serialize(Map.of("title", title, "priority", priority, "completed", completed));
    }

    private String serialize(Map<String, Object> value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Не удалось подготовить тело запроса в UI", exception);
        }
    }
}
