package ru.itmo.api;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class TaskRequest {
    @NotBlank
    @Size(min = 1, max = 100)
    @Schema(minLength = 1, pattern = "\\S", example = "Сдать лабораторную")
    public String title;

    @Min(1)
    @Max(5)
    @JsonSetter(nulls = Nulls.FAIL)
    @JsonDeserialize(using = PriorityDeserializer.class)
    @Schema(defaultValue = "3")
    public Integer priority = 3;

    @JsonSetter(nulls = Nulls.FAIL)
    @Schema(defaultValue = "false")
    public Boolean completed = false;
}
