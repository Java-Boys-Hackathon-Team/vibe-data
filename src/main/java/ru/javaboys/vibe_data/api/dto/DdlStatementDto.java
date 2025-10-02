package ru.javaboys.vibe_data.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "Описание DDL")
public class DdlStatementDto {
    @NotBlank
    @Schema(name = "Запрос")
    private String statement;
}
