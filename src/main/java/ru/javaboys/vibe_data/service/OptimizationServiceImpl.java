package ru.javaboys.vibe_data.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.javaboys.vibe_data.api.dto.OptimizationDto;
import ru.javaboys.vibe_data.domain.Optimization;
import ru.javaboys.vibe_data.mapper.OptimizationMapper;
import ru.javaboys.vibe_data.repository.OptimizationRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class OptimizationServiceImpl implements OptimizationService {
    private final OptimizationRepository repository;
    private final OptimizationMapper mapper;

    @Override
    public OptimizationDto save(String text) {
        Optimization optimization = new Optimization();
        optimization.setText(text);
        optimization = repository.save(optimization);

        return mapper.toDto(optimization);
    }

    @Override
    public OptimizationDto activate(UUID id) {
        Optimization optimization = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Entity not found, id: " + id));
        optimization.setActive(true);
        optimization = repository.save(optimization);

        return mapper.toDto(optimization);
    }

    @Override
    public OptimizationDto deactivate(UUID id) {
        Optimization optimization = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Entity not found, id: " + id));
        optimization.setActive(false);
        optimization = repository.save(optimization);

        return mapper.toDto(optimization);
    }

    @Override
    public OptimizationDto getById(UUID id) {
        return repository.findById(id)
                .map(mapper::toDto)
                .orElseThrow(() ->  new EntityNotFoundException("Entity not found, id: " + id));
    }

    @Override
    public List<OptimizationDto> findAllActive() {
        return repository.findAllByActiveIsTrue().stream()
                .map(mapper::toDto)
                .toList();
    }

}
