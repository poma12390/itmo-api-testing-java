package ru.itmo.ui.pages;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import ru.itmo.ui.UiResponse;

final class ServerResponsePanel {
    private final Locator table;
    private final Locator statusCode;
    private final Locator body;

    ServerResponsePanel(Locator operation) {
        table = operation.locator("css=table.live-responses-table");
        statusCode = table.locator("css=tbody td.response-col_status");
        body = table.locator("css=tbody td.response-col_description .highlight-code > pre");
    }

    void expectEmpty() {
        assertThat(table).hasCount(0);
    }

    UiResponse awaitResponse() {
        assertThat(table).isVisible();
        String statusText = statusCode.innerText().strip();
        int status = Integer.parseInt(statusText.split("\\s+")[0]);
        if (status == 204) {
            assertThat(body).hasCount(0);
            return new UiResponse(status, "");
        }
        assertThat(body).isVisible();
        assertThat(body).not().isEmpty();
        return new UiResponse(status, body.innerText().strip());
    }
}
