package ru.javaboys.vibe_data.api.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Создание новой задачи")
public class NewTaskRequestDto {

    @Schema(description = "Модель LLM")
    private String llmModel;

    @Min(0)
    @Max(1)
    @Schema(description = "Температура LLM")
    private Double temperature;

    @NotBlank
    @Schema(description = "URL")
    private String url;

    @NotEmpty
    @Valid
    @Schema(description = "Список DDL")
    private List<DdlStatementDto> ddl;

    @NotEmpty
    @Valid
    @Schema(description = "Список запросов")
    private List<QueryInputDto> queries;
}
