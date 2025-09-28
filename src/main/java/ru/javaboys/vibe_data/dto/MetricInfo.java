package ru.javaboys.vibe_data.dto;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class MetricInfo {
    private static final String ERROR_MESSAGE = "Ошибка получения информации";
    private final String explainResult;
    private final String error;

    private MetricInfo(String explainResult, String error) {
        this.explainResult = explainResult;
        this.error = error;
    }

    public static MetricInfo success(String explainResult) {
        return new MetricInfo(explainResult, null);
    }

    public static MetricInfo error(String error) {
        return new MetricInfo(null, error);
    }

    public static MetricInfo error() {
        return new MetricInfo(null, ERROR_MESSAGE);
    }

}
