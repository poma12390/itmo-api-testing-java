package ru.itmo.api;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(TaskNotFoundException.class)
    ResponseEntity<ApiError> notFound(TaskNotFoundException exception) {
        return ResponseEntity.status(404).body(new ApiError(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException exception) {
        String field = exception.getBindingResult().getFieldErrors().getFirst().getField();
        return invalid(field);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> invalidParameter(ConstraintViolationException exception) {
        String path = exception.getConstraintViolations().iterator().next().getPropertyPath().toString();
        return invalid(path.substring(path.lastIndexOf('.') + 1));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> invalidType(MethodArgumentTypeMismatchException exception) {
        return invalid(exception.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadableBody(HttpMessageNotReadableException exception) {
        if (exception.contains(JsonParseException.class)) {
            return ResponseEntity.badRequest().body(new ApiError("Malformed JSON", "body"));
        }
        Throwable cause = exception.getCause();
        if (cause instanceof UnrecognizedPropertyException unknown) {
            return invalid(unknown.getPropertyName());
        }
        if (cause instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
            return invalid(mapping.getPath().getFirst().getFieldName());
        }
        return ResponseEntity.badRequest().body(new ApiError("Request body is required", "body"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> unsupportedMethod(HttpRequestMethodNotSupportedException exception) {
        String allowed = exception.getSupportedHttpMethods().stream()
                .map(Object::toString).sorted().collect(Collectors.joining(", "));
        return ResponseEntity.status(405).header(HttpHeaders.ALLOW, allowed)
                .body(new ApiError("Method not allowed"));
    }

    private ResponseEntity<ApiError> invalid(String field) {
        return ResponseEntity.status(422).body(new ApiError("Validation failed", field));
    }
}
