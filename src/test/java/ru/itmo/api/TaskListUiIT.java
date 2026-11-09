package ru.itmo.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import ru.itmo.ui.UiResponse;

@Tag("ui")
@Tag("pagination")
@DisplayName("Список задач через Swagger UI")
@Timeout(value = 30, threadMode = Timeout.ThreadMode.SAME_THREAD)
class TaskListUiIT extends UiTestSupport {
    @Test
    @DisplayName("Без созданных задач список пуст")
    void returnsEmptyList() {
        assertTaskTitles(ui.listTasks(), List.of());
    }

    @ParameterizedTest(name = "{index}. limit={0}, offset={1}, поле {2}")
    @DisplayName("Swagger UI показывает ошибку для неверной пагинации")
    @CsvSource({
            "0, 0, limit, Value must be greater than or equal to 1",
            "101, 0, limit, Value must be less than or equal to 100",
            "1, -1, offset, Value must be greater than or equal to 0"
    })
    void rejectsInvalidPagination(int limit, int offset, String field, String message) {
        ui.expectPaginationError(limit, offset, field, message);
    }

    @Nested
    @DisplayName("В списке три задачи")
    class WithTasks {
        @BeforeEach
        void createTasks() {
            for (String title : List.of("Первая", "Вторая", "Третья")) {
                UiResponse created = ui.createTask(title);
                assertEquals(201, created.status(), created.body());
            }
        }

        @ParameterizedTest(name = "{index}. limit={0}, offset={1}")
        @DisplayName("Страница содержит нужные задачи в порядке создания")
        @MethodSource("pages")
        void returnsRequestedPage(int limit, int offset, List<String> expectedTitles) {
            assertTaskTitles(ui.listTasks(limit, offset), expectedTitles);
        }

        static Stream<Arguments> pages() {
            return Stream.of(
                    Arguments.of(1, 1, List.of("Вторая")),
                    Arguments.of(2, 1, List.of("Вторая", "Третья")),
                    Arguments.of(100, 0, List.of("Первая", "Вторая", "Третья")));
        }

        @Test
        @DisplayName("После последней задачи список пуст")
        void returnsEmptyPageAtEnd() {
            assertTaskTitles(ui.listTasks(1, 3), List.of());
        }
    }

    private void assertTaskTitles(UiResponse response, List<String> expectedTitles) {
        assertEquals(200, response.status(), response.body());
        JsonNode body = response.json();
        assertTrue(body.isArray(), response.body());
        List<String> titles = new ArrayList<>();
        body.forEach(task -> titles.add(task.path("title").asText()));
        assertEquals(expectedTitles, titles);
    }
}
