package ru.itmo.api.replication;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("replica")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReplicaHttpTest {
    @LocalServerPort
    int port;

    @Test
    void replicaNegotiatesHttp2AndReturnsVersionedBytes() throws Exception {
        URI uri = URI.create("http://127.0.0.1:" + port + ReplicaController.PATH + "?id=test");
        var read = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build();
        byte[] value = {0, -1, 42};
        var write = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5))
                .header(ReplicaController.VERSION, "1")
                .header("Content-Type", "application/octet-stream")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(value)).build();

        try (var client = HttpClient.newHttpClient()) {
            var missing = client.send(read, HttpResponse.BodyHandlers.discarding());
            assertEquals(404, missing.statusCode());
            assertEquals(HttpClient.Version.HTTP_2, missing.version());
            assertEquals(201, client.send(write, HttpResponse.BodyHandlers.discarding()).statusCode());
            var stored = client.send(read, HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, stored.statusCode());
            assertEquals(1L, stored.headers().firstValueAsLong(ReplicaController.VERSION).orElseThrow());
            assertArrayEquals(value, stored.body());
        }
    }
}
