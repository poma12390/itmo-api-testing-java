package ru.itmo.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import ru.itmo.ui.pages.SwaggerUiPage;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Execution(ExecutionMode.SAME_THREAD)
abstract class UiTestSupport {
    private static final Path ARTIFACTS = Path.of("target", "ui-artifacts");
    private static Playwright playwright;
    private static Browser browser;

    @LocalServerPort
    private int port;

    @Autowired
    private TaskService tasks;

    private BrowserContext context;
    private Page page;
    protected SwaggerUiPage ui;

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(!Boolean.getBoolean("ui.headed")));
    }

    @BeforeEach
    void prepareUi() throws IOException {
        tasks.reset();
        String baseUrl = "http://127.0.0.1:" + port;
        context = browser.newContext(new Browser.NewContextOptions()
                .setBaseURL(baseUrl).setViewportSize(1280, 900).setLocale("ru-RU"));
        context.setDefaultTimeout(10_000);
        context.tracing().start(new Tracing.StartOptions()
                .setScreenshots(true).setSnapshots(true).setSources(true));
        page = context.newPage();
        Files.createDirectories(ARTIFACTS);
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(
                ARTIFACTS.resolve("environment.json").toFile(), Map.of(
                        "recordedAt", ZonedDateTime.now(ZoneId.of("Europe/Samara")).toString(),
                        "java", System.getProperty("java.version"),
                        "os", System.getProperty("os.name") + " " + System.getProperty("os.version"),
                        "architecture", System.getProperty("os.arch"),
                        "playwright", System.getProperty("playwright.version", "unknown"),
                        "browser", "Chromium " + browser.version(),
                        "headless", !Boolean.getBoolean("ui.headed"),
                        "viewport", "1280x900",
                        "baseUrl", baseUrl));
        ui = new SwaggerUiPage(page).open();
    }

    @AfterEach
    void saveArtifactsAndCloseContext(TestInfo test) throws IOException {
        if (context == null) {
            return;
        }
        try {
            Path directory = ARTIFACTS.resolve(test.getTestMethod().orElseThrow().getName());
            Files.createDirectories(directory);
            try {
                if (page != null && !page.isClosed()) {
                    page.screenshot(new Page.ScreenshotOptions()
                            .setPath(directory.resolve("page.png")).setFullPage(true));
                    Files.writeString(directory.resolve("page.html"), page.content());
                }
            } finally {
                context.tracing().stop(new Tracing.StopOptions().setPath(directory.resolve("trace.zip")));
            }
        } finally {
            context.close();
            tasks.reset();
        }
    }

    @AfterAll
    static void closeBrowser() {
        if (playwright != null) {
            playwright.close();
        }
    }
}
