package ru.itmo.api;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.restassured.OpenApiValidationFilter;
import com.atlassian.oai.validator.restassured.RestAssuredResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class ApiTestSupport {
    @LocalServerPort
    int port;

    @Autowired
    TaskService tasks;

    @Autowired
    ObjectMapper json;

    @BeforeEach
    void resetStorage() {
        tasks.reset();
    }

    RequestSpecification api() {
        return given().baseUri("http://127.0.0.1").port(port).contentType(ContentType.JSON);
    }

    RequestSpecification validRequest() {
        return api().filter(new OpenApiValidationFilter(Contract.VALIDATOR));
    }

    RequestSpecification invalidRequest() {
        return api().filter((request, responseSpec, context) -> {
            var response = context.next(request, responseSpec);
            var report = Contract.VALIDATOR.validateResponse(
                    URI.create(request.getURI()).getPath(),
                    Request.Method.valueOf(request.getMethod()),
                    RestAssuredResponse.of(response));
            assertFalse(report.hasErrors(), report::toString);
            return response;
        });
    }

    Map<String, Object> createTask() {
        return validRequest().body(Map.of("title", "Подготовить API-тесты", "priority", 2))
                .post("/tasks").then().statusCode(201).extract().jsonPath().getMap("$");
    }

    private static class Contract {
        static final OpenApiInteractionValidator VALIDATOR = OpenApiInteractionValidator
                .createFor(Path.of("openapi.json").toAbsolutePath().toString()).build();
    }
}
