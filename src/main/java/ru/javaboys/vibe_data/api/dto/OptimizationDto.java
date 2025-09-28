package ru.javaboys.vibe_data.api.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class OptimizationDto {
    private final UUID id;
    private final String text;
    private final boolean active;

}
