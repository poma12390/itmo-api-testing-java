package ru.itmo.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/tasks", produces = "application/json")
@Validated
@ApiResponses({
        @ApiResponse(responseCode = "422", description = "Validation error",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "405", description = "Method not allowed",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class TaskController {
    private final TaskService tasks;

    public TaskController(TaskService tasks) {
        this.tasks = tasks;
    }

    @PostMapping
    @Operation(summary = "Create a task")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Task created",
                    content = @Content(schema = @Schema(implementation = Task.class)),
                    headers = @Header(name = "Location", required = true,
                            schema = @Schema(type = "string", pattern = "^/tasks/[1-9][0-9]*$"))),
            @ApiResponse(responseCode = "400", description = "Malformed JSON",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<Task> create(@Valid @RequestBody TaskRequest request) {
        Task task = tasks.create(request);
        return ResponseEntity.created(URI.create("/tasks/" + task.id())).body(task);
    }

    @GetMapping
    @Operation(summary = "List tasks")
    @ApiResponse(responseCode = "200", description = "Task list",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = Task.class))))
    public List<Task> list(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return tasks.list(limit, offset);
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "Get a task")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task found",
                    content = @Content(schema = @Schema(implementation = Task.class))),
            @ApiResponse(responseCode = "404", description = "Task not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public Task find(@PathVariable @Min(1) long taskId) {
        return tasks.find(taskId);
    }

    @PutMapping("/{taskId}")
    @Operation(summary = "Replace a task")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task replaced",
                    content = @Content(schema = @Schema(implementation = Task.class))),
            @ApiResponse(responseCode = "404", description = "Task not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "400", description = "Malformed JSON",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public Task replace(@PathVariable @Min(1) long taskId, @Valid @RequestBody TaskRequest request) {
        return tasks.replace(taskId, request);
    }

    @DeleteMapping("/{taskId}")
    @Operation(summary = "Delete a task")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Task deleted", content = @Content),
            @ApiResponse(responseCode = "404", description = "Task not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<Void> delete(@PathVariable @Min(1) long taskId) {
        tasks.delete(taskId);
        return ResponseEntity.noContent().build();
    }
}
