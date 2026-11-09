package ru.itmo.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Результат, прочитанный из блока Server response в интерфейсе. */
public record UiResponse(int status, String body) {
    private static final ObjectMapper JSON = new ObjectMapper();

    public JsonNode json() {
        try {
            return JSON.readTree(body);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("В Server response ожидался JSON, получено: " + body, exception);
        }
    }
}
