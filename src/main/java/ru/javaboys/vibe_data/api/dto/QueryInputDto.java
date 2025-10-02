package ru.javaboys.vibe_data.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "Запрос")
public class QueryInputDto {
    @NotBlank
    @Schema(name = "Идентификатор")
    private String queryid;

    @NotBlank
    @Schema(name = "Запрос")
    private String query;

    @Min(0)

    @Schema(name = "Количество запусков")
    private int runquantity;

    /**
     * Среднее время выполнения запроса в секундах (или другой агрегат), используется для оценки "веса".
     * Необязательное поле: если не задано или 0 — считаем равным 1 при сортировке.
     */
    @Min(0)
    @Schema(name = "Время выполнения")
    private int executiontime;
}
