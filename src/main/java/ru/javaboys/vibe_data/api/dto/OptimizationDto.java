package ru.javaboys.vibe_data.api.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
@Schema(description = "Оптимизация")
public class OptimizationDto {
    @Schema(description = "Идентификатор")
    private final UUID id;
    @Schema(description = "Описание")
    private final String text;
    @Schema(description = "Флаг активности")
    private final boolean active;

}
