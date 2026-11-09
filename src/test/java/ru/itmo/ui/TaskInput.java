package ru.itmo.ui;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskInput(String title, Integer priority, Boolean completed) {
    public TaskInput {
        Objects.requireNonNull(title, "title");
    }

    public static TaskInput titleOnly(String title) {
        return new TaskInput(title, null, null);
    }
}
