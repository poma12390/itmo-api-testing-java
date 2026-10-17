package ru.itmo.api;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.restassured.response.Response;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class TaskApiTest extends ApiTestSupport {
    @Test
    void createsAndReadsTask() {
        Map<String, Object> body = Map.of("title", "Сдать работу", "priority", 2, "completed", false);
        Response created = validRequest().body(body).post("/tasks");

        created.then().statusCode(201).contentType("application/json")
                .header("Location", "/tasks/1")
                .body("id", equalTo(1), "title", equalTo("Сдать работу"),
                        "priority", equalTo(2), "completed", equalTo(false));
        Response fetched = validRequest().get(created.header("Location"));
        fetched.then().statusCode(200);
        assertEquals(created.jsonPath().getMap("$"), fetched.jsonPath().getMap("$"));
    }

    @Test
    void appliesDefaultValues() {
        validRequest().body(Map.of("title", "Новая задача")).post("/tasks").then()
                .statusCode(201).body("priority", equalTo(3), "completed", equalTo(false));
    }

    @Test
    void initiallyReturnsEmptyList() {
        validRequest().get("/tasks").then().statusCode(200).body("$", hasSize(0));
    }

    @Test
    void paginatesInCreationOrder() {
        for (String title : List.of("Первая", "Вторая", "Третья")) {
            validRequest().body(Map.of("title", title)).post("/tasks").then().statusCode(201);
        }
        validRequest().queryParams("limit", 1, "offset", 1).get("/tasks").then()
                .statusCode(200).body("$", hasSize(1), "[0].title", equalTo("Вторая"));
        validRequest().queryParam("offset", 3).get("/tasks").then()
                .statusCode(200).body("$", hasSize(0));
    }

    @Test
    void replacesTaskAndPersistsChanges() {
        var task = createTask();
        var replacement = Map.of("title", "Проверить контракт", "priority", 5, "completed", true);
        Response updated = validRequest().body(replacement).put("/tasks/{id}", task.get("id"));

        updated.then().statusCode(200).body("id", equalTo(task.get("id")),
                "title", equalTo("Проверить контракт"), "priority", equalTo(5),
                "completed", equalTo(true));
        Response fetched = validRequest().get("/tasks/{id}", task.get("id"));
        fetched.then().statusCode(200);
        assertEquals(updated.jsonPath().getMap("$"), fetched.jsonPath().getMap("$"));
    }

    @Test
    void putResetsOmittedFieldsToDefaults() {
        var task = createTask();
        validRequest().body(Map.of("title", "Заменённая задача"))
                .put("/tasks/{id}", task.get("id")).then().statusCode(200)
                .body("priority", equalTo(3), "completed", equalTo(false));
    }

    @Test
    void deletesTaskAndReturnsEmptyBody() {
        var task = createTask();
        String path = "/tasks/" + task.get("id");
        validRequest().delete(path).then().statusCode(204).body(equalTo(""));
        validRequest().get(path).then().statusCode(404).body("detail", equalTo("Task not found"));
        validRequest().delete(path).then().statusCode(404);
        validRequest().get("/tasks").then().statusCode(200).body("$", hasSize(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT", "DELETE"})
    void missingTaskReturns404(String method) {
        var request = validRequest();
        if (method.equals("PUT")) {
            request.body(Map.of("title", "Отсутствующая задача"));
        }
        request.request(method, "/tasks/999").then()
                .statusCode(404).body("detail", equalTo("Task not found"));
        assertEquals(List.of(), validRequest().get("/tasks").jsonPath().getList("$"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT"})
    void acceptsIntegerValuedJsonNumbers(String method) {
        createTask();
        validRequest().body("{\"title\":\"Целое число\",\"priority\":4.0}")
                .request(method, pathFor(method)).then()
                .statusCode(successFor(method)).body("priority", equalTo(4));
    }

    @ParameterizedTest(name = "{0}: invalid {2} ({1})")
    @MethodSource("invalidBodies")
    void rejectsInvalidBodyWithoutChangingState(String method, String body, String field) {
        var task = createTask();
        invalidRequest().body(body).request(method, pathFor(method)).then()
                .statusCode(422).body("field", equalTo(field));
        assertEquals(List.of(task), validRequest().get("/tasks").jsonPath().getList("$"));
    }

    static Stream<Arguments> invalidBodies() {
        String[][] cases = {
                {"{}", "title"},
                {"{\"title\":\"\"}", "title"},
                {"{\"title\":\" \\t\\n\"}", "title"},
                {"{\"title\":\"" + "x".repeat(101) + "\"}", "title"},
                {"{\"title\":null}", "title"},
                {"{\"title\":123}", "title"},
                {"{\"title\":\"Задача\",\"priority\":0}", "priority"},
                {"{\"title\":\"Задача\",\"priority\":6}", "priority"},
                {"{\"title\":\"Задача\",\"priority\":\"3\"}", "priority"},
                {"{\"title\":\"Задача\",\"priority\":2.5}", "priority"},
                {"{\"title\":\"Задача\",\"priority\":true}", "priority"},
                {"{\"title\":\"Задача\",\"priority\":null}", "priority"},
                {"{\"title\":\"Задача\",\"completed\":\"false\"}", "completed"},
                {"{\"title\":\"Задача\",\"completed\":1}", "completed"},
                {"{\"title\":\"Задача\",\"completed\":null}", "completed"},
                {"{\"title\":\"Задача\",\"id\":10}", "id"}
        };
        return Stream.of("POST", "PUT").flatMap(method -> Arrays.stream(cases)
                .map(test -> Arguments.of(method, test[0], test[1])));
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT"})
    void rejectsMalformedJson(String method) {
        var task = createTask();
        invalidRequest().body("{\"title\":").request(method, pathFor(method)).then()
                .statusCode(400).body("field", equalTo("body"));
        assertEquals(List.of(task), validRequest().get("/tasks").jsonPath().getList("$"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT"})
    void rejectsInvalidUtf8(String method) {
        var task = createTask();
        byte[] body = {'{', '"', 't', 'i', 't', 'l', 'e', '"', ':', '"', (byte) 0xff, '"', '}'};
        invalidRequest().body(body).request(method, pathFor(method)).then()
                .statusCode(400).body("field", equalTo("body"));
        assertEquals(List.of(task), validRequest().get("/tasks").jsonPath().getList("$"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT"})
    void rejectsMissingBody(String method) {
        var task = createTask();
        invalidRequest().request(method, pathFor(method)).then()
                .statusCode(400).body("field", equalTo("body"));
        assertEquals(List.of(task), validRequest().get("/tasks").jsonPath().getList("$"));
    }

    @ParameterizedTest(name = "{0}: id={1}")
    @MethodSource("invalidIds")
    void rejectsInvalidPathParameter(String method, String id) {
        var request = invalidRequest();
        if (method.equals("PUT")) {
            request.body(Map.of("title", "Задача"));
        }
        request.request(method, "/tasks/{id}", id).then()
                .statusCode(422).body("field", equalTo("taskId"));
    }

    static Stream<Arguments> invalidIds() {
        return Stream.of("GET", "PUT", "DELETE").flatMap(method -> Stream.of("0", "-1", "abc", "1.5")
                .map(id -> Arguments.of(method, id)));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void acceptsPaginationBoundaries(int limit) {
        var task = createTask();
        Response response = validRequest().queryParams("limit", limit, "offset", 0).get("/tasks");
        response.then().statusCode(200);
        assertEquals(List.of(task), response.jsonPath().getList("$"));
    }

    @ParameterizedTest
    @CsvSource({"limit,0", "limit,101", "limit,abc", "limit,2147483648", "offset,-1", "offset,abc"})
    void rejectsInvalidPagination(String field, String value) {
        invalidRequest().queryParam(field, value).get("/tasks").then()
                .statusCode(422).body("field", equalTo(field));
    }

    @Test
    void largeOffsetReturnsEmptyList() {
        createTask();
        validRequest().queryParam("offset", Integer.MAX_VALUE).get("/tasks").then()
                .statusCode(200).body("$", hasSize(0));
    }

    @ParameterizedTest
    @CsvSource({"/tasks,GET POST", "/tasks/1,DELETE GET PUT"})
    void unsupportedMethodReturnsAllowedMethods(String path, String allowed) {
        var task = createTask();
        Response response = api().body(Map.of("title", "Не должно сохраниться")).patch(path);
        response.then().statusCode(405).body("detail", equalTo("Method not allowed"));
        Set<String> methods = Arrays.stream(response.header("Allow").split(","))
                .map(String::trim).collect(Collectors.toSet());
        assertEquals(Set.of(allowed.split(" ")), methods);
        assertEquals(List.of(task), validRequest().get("/tasks").jsonPath().getList("$"));
    }

    static String pathFor(String method) {
        return method.equals("POST") ? "/tasks" : "/tasks/1";
    }

    static int successFor(String method) {
        return method.equals("POST") ? 201 : 200;
    }
}
