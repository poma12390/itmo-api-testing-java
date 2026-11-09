package ru.itmo.api.replication;

import java.util.concurrent.CompletableFuture;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("coordinator")
class EntityController {
    private final ReplicaClient replicas;

    EntityController(ReplicaClient replicas) {
        this.replicas = replicas;
    }

    @GetMapping(value = ReplicaController.PATH, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    CompletableFuture<ResponseEntity<byte[]>> get(@RequestParam String id) {
        return replicas.request(id, null);
    }

    @PutMapping(ReplicaController.PATH)
    CompletableFuture<ResponseEntity<byte[]>> put(@RequestParam String id,
                                                  @RequestBody(required = false) byte[] body) {
        return replicas.request(id, body == null ? new byte[0] : body);
    }
}
