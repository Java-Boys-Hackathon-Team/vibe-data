package ru.javaboys.vibe_data.service;

import java.util.List;
import java.util.UUID;

import ru.javaboys.vibe_data.api.dto.OptimizationDto;

public interface OptimizationService {
    OptimizationDto save(String text);

    OptimizationDto activate(UUID id);

    OptimizationDto deactivate(UUID id);

    OptimizationDto getById(UUID id);

    List<OptimizationDto> findAllActive();
}
