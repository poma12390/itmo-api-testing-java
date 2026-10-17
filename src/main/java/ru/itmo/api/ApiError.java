package ru.itmo.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record ApiError(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String detail,
        String field) {
    public ApiError(String detail) {
        this(detail, null);
    }
}
