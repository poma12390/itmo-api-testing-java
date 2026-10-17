package ru.itmo.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record Task(
        @Min(1) @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Size(min = 1, max = 100) @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @Min(1) @Max(5) @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int priority,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean completed) {}
