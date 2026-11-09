package ru.itmo.ui.pages;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;
import ru.itmo.ui.UiResponse;

/** Общая часть пяти раскрывающихся операций Swagger UI. */
final class OperationPanel {
    private final Locator panel;

    OperationPanel(Locator panel) {
        this.panel = panel;
    }

    OperationPanel edit() {
        Locator toggle = panel.locator(".opblock-summary-control");
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
        panel.locator("textarea.body-param__text").fill(json);
        return this;
    }

    UiResponse execute() {
        submit();
        Locator response = serverResponse();
        assertThat(response).isVisible();

        String statusText = response.locator("tbody .response-col_status").innerText().strip();
        int status = Integer.parseInt(statusText.split("\\s+")[0]);
        Locator responseBody = response.locator("tbody .response-col_description .highlight-code pre");
        if (status == 204) {
            assertThat(responseBody).hasCount(0);
            return new UiResponse(status, "");
        }
        assertThat(responseBody).isVisible();
        assertThat(responseBody).not().isEmpty();
        return new UiResponse(status, responseBody.innerText().strip());
    }

    void expectParameterError(String name, String message) {
        submit();
        assertThat(parameterInput(name)).hasAttribute("title", message);
        assertThat(panel.locator(".validation-errors").getByRole(AriaRole.LISTITEM))
                .hasText("For '%s': %s.".formatted(name, message));
        assertThat(serverResponse()).hasCount(0);
    }

    private void submit() {
        Locator clear = button("Clear");
        if (clear.isVisible()) {
            clear.click();
        }
        // Старый ответ должен исчезнуть: иначе повторный GET мог бы прочитать прошлый результат.
        assertThat(serverResponse()).hasCount(0);
        button("Execute").click();
    }

    private Locator serverResponse() {
        return panel.locator(".live-responses-table");
    }

    private Locator parameterInput(String name) {
        return panel.locator("tr[data-param-name='" + name + "']").getByRole(AriaRole.TEXTBOX);
    }

    private Locator button(String name) {
        return panel.getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName(name).setExact(true));
    }
}
