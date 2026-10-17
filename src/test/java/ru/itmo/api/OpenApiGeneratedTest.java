package ru.itmo.api;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class OpenApiGeneratedTest extends ApiTestSupport {
    @TestFactory
    List<DynamicTest> operationsFromSpecification() throws Exception {
        JsonNode specification = json.readTree(Files.readString(Path.of("openapi.json")));
        List<DynamicTest> tests = new ArrayList<>();
        specification.path("paths").fields().forEachRemaining(path ->
                path.getValue().fields().forEachRemaining(operation -> {
                    String method = operation.getKey().toUpperCase(Locale.ROOT);
                    if (!List.of("GET", "POST", "PUT", "DELETE").contains(method)) {
                        return;
                    }
                    String template = path.getKey();
                    JsonNode definition = operation.getValue();
                    tests.add(DynamicTest.dynamicTest(method + " " + template, () -> {
                        tasks.reset();
                        createTask();
                        var request = validRequest();
                        if (definition.has("requestBody")) {
                            request.body(sampleBody(requestSchema(specification, definition)));
                        }
                        for (JsonNode parameter : definition.path("parameters")) {
                            if (parameter.path("in").asText().equals("query")) {
                                JsonNode schema = parameter.path("schema");
                                request.queryParam(parameter.path("name").asText(),
                                        schema.path("default").asInt(schema.path("minimum").asInt()));
                            }
                        }
                        request.request(method, template.replaceAll("\\{[^}]+}", "1"))
                                .then().statusCode(successStatus(definition));
                    }));
                    if (definition.has("requestBody")) {
                        addBoundaryTests(tests, specification, template, method, definition);
                    }
                }));
        return tests;
    }

    private void addBoundaryTests(List<DynamicTest> tests, JsonNode specification,
            String template, String method, JsonNode operation) {
        JsonNode schema = requestSchema(specification, operation);
        JsonNode title = schema.path("properties").path("title");
        JsonNode priority = schema.path("properties").path("priority");
        int minLength = title.path("minLength").asInt();
        int maxLength = title.path("maxLength").asInt();
        int minPriority = priority.path("minimum").asInt();
        int maxPriority = priority.path("maximum").asInt();
        String path = template.replaceAll("\\{[^}]+}", "1");

        for (int length : new int[] {minLength, maxLength}) {
            for (int value : new int[] {minPriority, maxPriority}) {
                tests.add(DynamicTest.dynamicTest(method + " title.length=" + length
                        + ", priority=" + value, () -> {
                            tasks.reset();
                            createTask();
                            ObjectNode body = sampleBody(schema).put("title", "x".repeat(length))
                                    .put("priority", value);
                            validRequest().body(body.toString()).request(method, path).then()
                                    .statusCode(successStatus(operation))
                                    .body("title", equalTo("x".repeat(length)), "priority", equalTo(value));
                        }));
            }
        }
        for (int length : new int[] {minLength - 1, maxLength + 1}) {
            tests.add(invalidBoundary(method + " title.length=" + length, method, path,
                    sampleBody(schema).put("title", "x".repeat(length)), "title"));
        }
        for (int value : new int[] {minPriority - 1, maxPriority + 1}) {
            tests.add(invalidBoundary(method + " priority=" + value, method, path,
                    sampleBody(schema).put("priority", value), "priority"));
        }
    }

    private DynamicTest invalidBoundary(String name, String method, String path,
            ObjectNode body, String field) {
        return DynamicTest.dynamicTest(name, () -> {
            tasks.reset();
            Map<String, Object> task = createTask();
            invalidRequest().body(body.toString()).request(method, path).then()
                    .statusCode(422).body("field", equalTo(field));
            assertEquals(List.of(task), validRequest().get("/tasks").jsonPath().getList("$"));
        });
    }

    private JsonNode requestSchema(JsonNode specification, JsonNode operation) {
        JsonNode schema = operation.path("requestBody").path("content")
                .path("application/json").path("schema");
        return schema.has("$ref") ? specification.at(schema.path("$ref").asText().substring(1)) : schema;
    }

    private ObjectNode sampleBody(JsonNode schema) {
        ObjectNode body = json.createObjectNode();
        schema.path("properties").fields().forEachRemaining(property -> {
            JsonNode definition = property.getValue();
            if (definition.has("default")) {
                body.set(property.getKey(), definition.get("default"));
            } else if (definition.has("example")) {
                body.set(property.getKey(), definition.get("example"));
            } else {
                switch (definition.path("type").asText()) {
                    case "string" -> body.put(property.getKey(), "x".repeat(
                            Math.max(1, definition.path("minLength").asInt())));
                    case "integer" -> body.put(property.getKey(), definition.path("minimum").asInt());
                    case "boolean" -> body.put(property.getKey(), false);
                    default -> throw new IllegalArgumentException("Unsupported schema: " + definition);
                }
            }
        });
        return body;
    }

    private int successStatus(JsonNode operation) {
        var codes = operation.path("responses").fieldNames();
        while (codes.hasNext()) {
            String code = codes.next();
            if (code.matches("2[0-9]{2}")) {
                return Integer.parseInt(code);
            }
        }
        throw new IllegalArgumentException("Success response is not defined");
    }
}
