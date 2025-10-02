package ru.javaboys.vibe_data.api.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "Ответ на создание оптимизации")
public class NewOptimizationResponseDto {
    @NotNull
    @Schema(name = "Идентификатор")
    private UUID id;

}
