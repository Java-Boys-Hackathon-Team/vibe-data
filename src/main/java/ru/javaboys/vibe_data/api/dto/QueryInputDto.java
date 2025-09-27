package ru.javaboys.vibe_data.api.dto;

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
public class QueryInputDto {
    @NotBlank
    private String queryid;

    @NotBlank
    private String query;

    @Min(0)
    private int runquantity;

    /**
     * Среднее время выполнения запроса в секундах (или другой агрегат), используется для оценки "веса".
     * Необязательное поле: если не задано или 0 — считаем равным 1 при сортировке.
     */
    @Min(0)
    private int executiontime;
}
