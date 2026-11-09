package ru.itmo.api.replication;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@Profile("coordinator")
class ReplicaClient implements AutoCloseable {
    private static final Reply UNAVAILABLE = new Reply(503, 0, null);
    private final List<URI> endpoints;
    private final Duration timeout;
    private final AtomicLong version = new AtomicLong();
    private final ExecutorService workers;
    private final HttpClient client;

    ReplicaClient(@Value("${replication.replicas}") List<URI> replicas,
                  @Value("${replication.timeout-ms:2000}") long timeoutMillis,
                  @Value("${replication.http-threads:8}") int threads) {
        endpoints = replicas.stream().map(uri -> uri.resolve(ReplicaController.PATH)).toList();
        if (endpoints.isEmpty() || endpoints.stream().distinct().count() != endpoints.size()) {
            throw new IllegalArgumentException("Configure distinct replica addresses");
        }
        endpoints.forEach(uri -> HttpRequest.newBuilder(uri).build());
        timeout = Duration.ofMillis(timeoutMillis);
        workers = Executors.newFixedThreadPool(threads, Thread.ofPlatform().name("replica-http-", 1).factory());
        client = HttpClient.newBuilder().executor(workers).connectTimeout(timeout).build();
    }

    @PostConstruct
    void warmConnections() {
        // Allow the HTTP/2 upgrade to finish even if the first client request reaches quorum early.
        for (URI endpoint : endpoints) {
            var probe = HttpRequest.newBuilder(URI.create(endpoint + "?id=")).timeout(timeout)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
            client.sendAsync(probe, HttpResponse.BodyHandlers.discarding());
        }
    }

    CompletableFuture<ResponseEntity<byte[]>> request(String id, byte[] body) {
        boolean write = body != null;
        long revision = write ? version.updateAndGet(previous ->
                Math.max(previous + 1, System.currentTimeMillis() * 1_000_000)) : 0;
        String query = "?id=" + URLEncoder.encode(id, StandardCharsets.UTF_8);
        List<HttpRequest> requests = endpoints.stream().map(endpoint -> {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(endpoint + query)).timeout(timeout);
            if (write) {
                request.header(ReplicaController.VERSION, Long.toString(revision))
                        .header("Content-Type", MediaType.APPLICATION_OCTET_STREAM_VALUE)
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(body));
            }
            return request.build();
        }).toList();
        return exchange(requests, write).thenApply(Reply::response);
    }

    private CompletableFuture<Reply> exchange(List<HttpRequest> requests, boolean write) {
        Quorum quorum = new Quorum(requests.size());
        var inFlight = new ArrayList<CompletableFuture<HttpResponse<byte[]>>>(requests.size());
        var result = new CompletableFuture<Reply>();
        result.completeOnTimeout(UNAVAILABLE, timeout.toMillis(), TimeUnit.MILLISECONDS);
        for (HttpRequest request : requests) {
            var exchange = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
            inFlight.add(exchange);
            exchange.whenComplete((response, error) -> {
                Reply decision = quorum.record(error == null ? Reply.from(response, write) : null);
                if (decision != null) {
                    result.complete(decision);
                }
            });
        }
        // Register cancellation after dispatch so even an immediate quorum starts every exchange.
        return result.whenComplete((reply, error) -> inFlight.forEach(request -> request.cancel(true)));
    }

    @Override
    public void close() {
        client.close();
        workers.shutdown();
    }

    private static final class Quorum {
        private final int required;
        private int remaining;
        private int accepted;
        private Reply newest;
        private boolean decided;

        Quorum(int replicas) {
            required = replicas / 2 + 1;
            remaining = replicas;
        }

        synchronized Reply record(Reply reply) {
            if (decided) {
                return null;
            }
            remaining--;
            if (reply != null) {
                accepted++;
                if (newest == null || reply.version() > newest.version()) {
                    newest = reply;
                }
            }
            if (accepted == required) {
                decided = true;
                return newest;
            }
            if (accepted + remaining < required) {
                decided = true;
                return UNAVAILABLE;
            }
            return null;
        }
    }

    private record Reply(int status, long version, byte[] body) {
        static Reply from(HttpResponse<byte[]> response, boolean write) {
            int status = response.statusCode();
            if (write ? status == 201 : status == 404) {
                return new Reply(status, 0, null);
            }
            if (!write && status == 200) {
                try {
                    long revision = response.headers().firstValueAsLong(ReplicaController.VERSION).orElse(0);
                    if (revision > 0) {
                        return new Reply(status, revision, response.body());
                    }
                } catch (NumberFormatException ignored) {
                    // A malformed version cannot participate in a read quorum.
                }
            }
            return null;
        }

        ResponseEntity<byte[]> response() {
            if (status == HttpStatus.OK.value()) {
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(body);
            }
            return ResponseEntity.status(status).build();
        }
    }
}
