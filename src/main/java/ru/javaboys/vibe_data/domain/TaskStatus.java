package ru.javaboys.vibe_data.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Status of processing task.
 */
public enum TaskStatus {
    @Schema(name = "В работе")
    RUNNING,
    @Schema(name = "Выполнено")
    DONE,
    @Schema(name = "Завершен с ошибкой")
    FAILED
}
