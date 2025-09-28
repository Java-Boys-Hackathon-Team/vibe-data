package ru.javaboys.vibe_data.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import ru.javaboys.vibe_data.api.dto.OptimizationDto;
import ru.javaboys.vibe_data.domain.Optimization;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OptimizationMapper {

    OptimizationDto toDto(Optimization entity);

}
