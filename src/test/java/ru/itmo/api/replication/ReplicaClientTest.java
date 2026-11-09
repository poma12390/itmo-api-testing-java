package ru.itmo.api.replication;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.HttpStatus;

@Timeout(15)
class ReplicaClientTest {
    private final List<HttpServer> servers = new ArrayList<>();
    private final CountDownLatch release = new CountDownLatch(1);
    private final byte[] value = "данные".getBytes(StandardCharsets.UTF_8);

    @AfterEach
    void stopServers() {
        release.countDown();
        servers.forEach(server -> server.stop(0));
    }

    @Test
    void contactsEveryReplicaAndReturnsTheNewestOfTwoFastReads() throws Exception {
        var arrived = new CountDownLatch(3);
        URI fast = replica(exchange -> {
            arrived.countDown();
            await(arrived);
            respond(exchange, 200, "42", value);
        });
        URI older = replica(exchange -> {
            arrived.countDown();
            await(arrived);
            respond(exchange, 200, "40", new byte[]{1});
        });
        try (var listener = Executors.newVirtualThreadPerTaskExecutor();
             var server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            var canceled = listener.submit(() -> {
                try (var socket = server.accept()) {
                    socket.setSoTimeout(5_000);
                    var input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                    String line;
                    do {
                        line = input.readLine();
                    } while (line != null && !line.isEmpty());
                    if (line == null) {
                        return false;
                    }
                    arrived.countDown();
                    try {
                        return input.readLine() == null;
                    } catch (SocketException reset) {
                        return true;
                    }
                }
            });
            URI slow = URI.create("http://127.0.0.1:" + server.getLocalPort());
            try (var client = new ReplicaClient(List.of(slow, fast, older), 10_000, 4)) {
                var response = client.request("a +/&я", null).get(4, TimeUnit.SECONDS);
                assertEquals(0, arrived.getCount());
                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertArrayEquals(value, response.getBody());
                assertTrue(canceled.get(6, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void readsFromTheIntersectionOfWriteAndReadQuorums() throws Exception {
        var updated = new ReplicaController();
        var writeOnly = new ReplicaController();
        var stale = new ReplicaController();
        URI first = replica(exchange -> serve(exchange, updated));
        URI second = replica(exchange -> {
            if (exchange.getRequestMethod().equals("PUT")) {
                serve(exchange, writeOnly);
            } else {
                respond(exchange, 500, null, null);
            }
        });
        URI third = replica(exchange -> {
            if (exchange.getRequestMethod().equals("PUT")) {
                respond(exchange, 500, null, null);
            } else {
                serve(exchange, stale);
            }
        });
        try (var client = new ReplicaClient(List.of(first, second, third), 2_000, 4)) {
            assertEquals(HttpStatus.NOT_FOUND, client.request("key", null).get().getStatusCode());
            assertEquals(HttpStatus.CREATED, client.request("key", value).get().getStatusCode());
            assertArrayEquals(value, client.request("key", null).get().getBody());
            assertEquals(HttpStatus.CREATED, client.request("key", new byte[0]).get().getStatusCode());
            assertArrayEquals(new byte[0], client.request("key", null).get().getBody());
        }
    }

    @Test
    void failsAsSoonAsTwoReplicasCannotVote() throws Exception {
        URI error = replica(exchange -> respond(exchange, 500, null, null));
        URI malformed = replica(exchange -> respond(exchange, 200, "bad", value));
        URI slow = replica(exchange -> await(release));
        try (var client = new ReplicaClient(List.of(error, malformed, slow), 10_000, 4)) {
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                    client.request("key", null).get(4, TimeUnit.SECONDS).getStatusCode());
        }
    }

    @Test
    void deadlineAlsoAppliesToIncompleteResponseBodies() throws Exception {
        var headersSent = new CountDownLatch(1);
        URI hanging = replica(exchange -> {
            exchange.getResponseHeaders().set(ReplicaController.VERSION, "1");
            exchange.sendResponseHeaders(200, 2);
            exchange.getResponseBody().write(1);
            exchange.getResponseBody().flush();
            headersSent.countDown();
            await(release);
        });
        URI healthy = replica(exchange -> respond(exchange, 200, "2", value));
        URI error = replica(exchange -> respond(exchange, 500, null, null));
        try (var client = new ReplicaClient(List.of(healthy, error, hanging), 1_000, 4)) {
            var result = client.request("key", null);
            assertTrue(headersSent.await(3, TimeUnit.SECONDS));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, result.get(4, TimeUnit.SECONDS).getStatusCode());
        }
    }

    @Test
    void aDelayedWriteCannotReplaceANewerValue() {
        var replica = new ReplicaController();
        replica.put("key", 2, value);
        replica.put("key", 1, null);
        assertArrayEquals(value, replica.get("key").getBody());
        assertEquals("2", replica.get("key").getHeaders().getFirst(ReplicaController.VERSION));
    }

    private URI replica(HttpHandler handler) throws IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext(ReplicaController.PATH, exchange -> {
            try (exchange) {
                handler.handle(exchange);
            }
        });
        server.start();
        servers.add(server);
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static void serve(HttpExchange exchange, ReplicaController replica) throws IOException {
        if (exchange.getRequestMethod().equals("PUT")) {
            long version = Long.parseLong(exchange.getRequestHeaders().getFirst(ReplicaController.VERSION));
            var response = replica.put("key", version, exchange.getRequestBody().readAllBytes());
            respond(exchange, response.getStatusCode().value(), null, null);
        } else {
            var response = replica.get("key");
            respond(exchange, response.getStatusCode().value(),
                    response.getHeaders().getFirst(ReplicaController.VERSION), response.getBody());
        }
    }

    private static void respond(HttpExchange exchange, int status, String version, byte[] body) throws IOException {
        if (version != null) {
            exchange.getResponseHeaders().set(ReplicaController.VERSION, version);
        }
        exchange.sendResponseHeaders(status, body == null || body.length == 0 ? -1 : body.length);
        if (body != null && body.length > 0) {
            exchange.getResponseBody().write(body);
        }
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(12, TimeUnit.SECONDS)) {
                throw new IOException("Replica test did not release the response");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException(error);
        }
    }
}
