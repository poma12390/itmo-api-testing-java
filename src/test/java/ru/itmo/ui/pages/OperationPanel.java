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
        panel.locator("tr[data-param-name='taskId'] input").fill(Long.toString(id));
        return this;
    }

    OperationPanel body(String json) {
        panel.locator("textarea.body-param__text").fill(json);
        return this;
    }

    UiResponse execute() {
        Locator response = panel.locator(".live-responses-table");
        Locator clear = button("Clear");
        if (clear.isVisible()) {
            clear.click();
        }
        // Старый ответ должен исчезнуть: иначе повторный GET мог бы прочитать прошлый результат.
        assertThat(response).hasCount(0);
        button("Execute").click();
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

    private Locator button(String name) {
        return panel.getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName(name).setExact(true));
    }
}
