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
@Schema(name = "Создание новой задачи")
public class NewTaskRequestDto {

    @Schema(name = "Модель LLM")
    private String llmModel;

    @Min(0)
    @Max(1)
    @Schema(name = "Температура LLM")
    private Double temperature;

    @NotBlank
    @Schema(name = "URL")
    private String url;

    @NotEmpty
    @Valid
    @Schema(name = "Список DDL")
    private List<DdlStatementDto> ddl;

    @NotEmpty
    @Valid
    @Schema(name = "Список запросов")
    private List<QueryInputDto> queries;
}
