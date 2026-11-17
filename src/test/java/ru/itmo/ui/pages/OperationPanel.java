package ru.itmo.ui.pages;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import ru.itmo.ui.UiResponse;

/** Общая часть пяти раскрывающихся операций Swagger UI. */
final class OperationPanel {
    private final Locator panel;
    private final Locator toggle;
    private final Locator requestBody;
    private final Locator validationErrors;
    private final ServerResponsePanel response;

    OperationPanel(Locator panel) {
        this.panel = panel;
        toggle = panel.locator("css=button.opblock-summary-control[aria-expanded]");
        requestBody = panel.locator("css=textarea.body-param__text");
        validationErrors = panel.locator("css=.validation-errors li");
        response = new ServerResponsePanel(panel);
    }

    OperationPanel edit() {
        if (!"true".equals(toggle.getAttribute("aria-expanded"))) {
            toggle.click();
        }
        Locator tryItOut = button("Try it out");
        assertThat(tryItOut.or(button("Cancel"))).isVisible();
        if (tryItOut.isVisible()) {
            tryItOut.click();
        }
        assertThat(button("Execute")).isVisible();
        return this;
    }

    OperationPanel taskId(long id) {
        return parameter("taskId", Long.toString(id));
    }

    OperationPanel parameter(String name, String value) {
        parameterInput(name).fill(value);
        return this;
    }

    OperationPanel body(String json) {
        requestBody.fill(json);
        return this;
    }

    UiResponse execute() {
        submit();
        return response.awaitResponse();
    }

    void expectParameterError(String name, String message) {
        submit();
        assertThat(parameterInput(name)).hasAttribute("title", message);
        assertThat(validationErrors)
                .hasText("For '%s': %s.".formatted(name, message));
        response.expectEmpty();
    }

    private void submit() {
        Locator clear = button("Clear");
        if (clear.isVisible()) {
            clear.click();
        }
        // Старый ответ должен исчезнуть: иначе повторный GET мог бы прочитать прошлый результат.
        response.expectEmpty();
        button("Execute").click();
    }

    private Locator parameterInput(String name) {
        return panel.locator("css=tr[data-param-name='%s'] input[type='text']".formatted(name));
    }

    private Locator button(String name) {
        return panel.locator("xpath=.//button[normalize-space(.)='%s']".formatted(name));
    }
}
