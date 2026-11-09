package ru.itmo.ui.pages;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import ru.itmo.ui.UiResponse;

final class ServerResponsePanel {
    private final Locator table;

    ServerResponsePanel(Locator operation) {
        table = operation.locator(".live-responses-table");
    }

    void expectEmpty() {
        assertThat(table).hasCount(0);
    }

    UiResponse awaitResponse() {
        assertThat(table).isVisible();
        String statusText = table.locator("tbody .response-col_status").innerText().strip();
        int status = Integer.parseInt(statusText.split("\\s+")[0]);
        Locator body = table.locator("tbody .response-col_description .highlight-code pre");
        if (status == 204) {
            assertThat(body).hasCount(0);
            return new UiResponse(status, "");
        }
        assertThat(body).isVisible();
        assertThat(body).not().isEmpty();
        return new UiResponse(status, body.innerText().strip());
    }
}
