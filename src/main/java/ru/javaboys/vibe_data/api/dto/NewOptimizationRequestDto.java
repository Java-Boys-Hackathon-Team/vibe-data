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
@Schema(description = "Запрос на создание новой оптимизации")
public class NewOptimizationRequestDto {
    @NotBlank
    @Schema(description = "Описание")
    private String text;

}
