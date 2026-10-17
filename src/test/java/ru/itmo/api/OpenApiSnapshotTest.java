package ru.itmo.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OpenApiSnapshotTest extends ApiTestSupport {
    @Test
    void specificationMatchesApplication() throws Exception {
        String response = api().get("/openapi.json").then()
                .statusCode(200).extract().asString();
        var actual = json.readTree(response);
        Path file = Path.of("openapi.json");
        if (Boolean.getBoolean("updateOpenApi")) {
            Files.writeString(file, json.writerWithDefaultPrettyPrinter().writeValueAsString(actual) + "\n");
        }
        assertEquals(json.readTree(Files.readString(file)), actual,
                "Обновить контракт: ./mvnw -Dtest=OpenApiSnapshotTest -DupdateOpenApi=true test");
    }
}
