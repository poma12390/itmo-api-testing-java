package ru.itmo.api.replication;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("replica")
class ReplicaController {
    static final String PATH = "/v0/entity";
    static final String VERSION = "X-Entity-Version";

    private final ConcurrentHashMap<String, Value> values = new ConcurrentHashMap<>();

    @GetMapping(PATH)
    ResponseEntity<byte[]> get(@RequestParam String id) {
        Value value = values.get(id);
        if (value == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().header(VERSION, Long.toString(value.version())).body(value.body());
    }

    @PutMapping(PATH)
    ResponseEntity<Void> put(@RequestParam String id, @RequestHeader(VERSION) long version,
                             @RequestBody(required = false) byte[] body) {
        Value update = new Value(version, body == null ? new byte[0] : body);
        values.merge(id, update, (old, next) -> next.version() > old.version() ? next : old);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    private record Value(long version, byte[] body) {}
}
