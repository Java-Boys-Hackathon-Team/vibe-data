package ru.javaboys.vibe_data.dto;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class TrinoResponse {
    private static final String ERROR_MESSAGE = "Ошибка получения информации";
    private final String response;
    private final String error;

    private TrinoResponse(String response, String error) {
        this.response = response;
        this.error = error;
    }

    public static TrinoResponse success(String explainResult) {
        return new TrinoResponse(explainResult, null);
    }

    public static TrinoResponse error(String error) {
        return new TrinoResponse(null, error);
    }

    public static TrinoResponse error() {
        return new TrinoResponse(null, ERROR_MESSAGE);
    }

}
