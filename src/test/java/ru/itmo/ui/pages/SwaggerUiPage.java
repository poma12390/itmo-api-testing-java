package ru.itmo.ui.pages;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import ru.itmo.ui.TaskInput;
import ru.itmo.ui.UiResponse;

public final class SwaggerUiPage {
    private final Page page;
    private final ObjectMapper json = new ObjectMapper();

    public SwaggerUiPage(Page page) {
        this.page = page;
    }

    public SwaggerUiPage open() {
        page.navigate("/swagger-ui/index.html");
        return awaitLoaded();
    }

    public SwaggerUiPage reload() {
        page.reload();
        return awaitLoaded();
    }

    public UiResponse createTask(String title) {
        return createTask(TaskInput.titleOnly(title));
    }

    public UiResponse createTask(String title, int priority, boolean completed) {
        return createTask(new TaskInput(title, priority, completed));
    }

    public UiResponse createTask(TaskInput task) {
        return operation("create").body(serialize(task)).execute();
    }

    public UiResponse readTask(long id) {
        return operation("find").taskId(id).execute();
    }

    public UiResponse replaceTask(long id, String title, int priority, boolean completed) {
        return replaceTask(id, new TaskInput(title, priority, completed));
    }

    public UiResponse replaceTask(long id, TaskInput task) {
        return operation("replace").taskId(id).body(serialize(task)).execute();
    }

    public UiResponse deleteTask(long id) {
        return operation("delete").taskId(id).execute();
    }

    public UiResponse listTasks() {
        return operation("list").execute();
    }

    public UiResponse listTasks(int limit, int offset) {
        return listOperation(limit, offset).execute();
    }

    public void expectPaginationError(int limit, int offset, String field, String message) {
        listOperation(limit, offset).expectParameterError(field, message);
    }

    private OperationPanel listOperation(int limit, int offset) {
        return operation("list")
                .parameter("limit", Integer.toString(limit))
                .parameter("offset", Integer.toString(offset));
    }

    private SwaggerUiPage awaitLoaded() {
        assertThat(page.locator(".info .title")).containsText("Tasks API");
        assertThat(operationBlock("create")).isVisible();
        assertThat(operationBlock("list")).isVisible();
        return this;
    }

    private OperationPanel operation(String operationId) {
        return new OperationPanel(operationBlock(operationId)).edit();
    }

    private Locator operationBlock(String operationId) {
        return page.locator("#operations-task-controller-" + operationId);
    }

    private String serialize(TaskInput task) {
        try {
            return json.writeValueAsString(task);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Не удалось подготовить тело запроса в UI", exception);
        }
    }
}
