package ru.javaboys.vibe_data.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Создание новой задачи")
public class NewTaskRequestDto {

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
