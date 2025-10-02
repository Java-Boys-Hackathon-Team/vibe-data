package ru.javaboys.vibe_data.api.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
@Schema(name = "Оптимизация")
public class OptimizationDto {
    @Schema(name = "Идентификатор")
    private final UUID id;
    @Schema(name = "Описание")
    private final String text;
    @Schema(name = "Флаг активности")
    private final boolean active;

}
