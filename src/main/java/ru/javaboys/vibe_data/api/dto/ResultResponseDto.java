package ru.javaboys.vibe_data.api.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Идентификатор")
public class ResultResponseDto {
    @NotEmpty
    @Valid
    @Schema(description = "Список DDL")
    private List<SqlBlockDto> ddl;

    @NotEmpty
    @Valid
    @Schema(description = "Список миграций")
    private List<SqlBlockDto> migrations;

    @NotEmpty
    @Valid
    @Schema(description = "Список запросов")
    private List<RewrittenQueryDto> queries;
}
