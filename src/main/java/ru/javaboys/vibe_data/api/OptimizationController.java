package ru.javaboys.vibe_data.api;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.javaboys.vibe_data.api.dto.NewOptimizationRequestDto;
import ru.javaboys.vibe_data.api.dto.NewOptimizationResponseDto;
import ru.javaboys.vibe_data.api.dto.OptimizationDto;
import ru.javaboys.vibe_data.service.OptimizationService;

@Slf4j
@RestController
@RequestMapping("/api/v1/optimizations")
@RequiredArgsConstructor
@Tag(name = "Оптимизации")
public class OptimizationController {
    private final OptimizationService service;

    @Operation(summary = "Создать новую оптимизацию", description = "Возвращает данные созданной оптимизации")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Успешное создание")
    })
    @PostMapping("/new")
    public ResponseEntity<NewOptimizationResponseDto> create(@Valid @RequestBody NewOptimizationRequestDto request) {
        log.info("Принят запрос на создание оптимизации");
        try {
            UUID id = service.save(request.getText()).getId();
            log.info("Оптимизация создана, id={}", id);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(NewOptimizationResponseDto.builder().id(id).build());
        } catch (Exception e) {
            log.error("Ошибка при создании оптимизации: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Operation(summary = "Получить оптимизацию", description = "Возвращает оптимизацию")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Оптимизация найдена"),
            @ApiResponse(responseCode = "404", description = "Оптимизация не найдена")
    })
    @GetMapping("/{id}")
    public ResponseEntity<OptimizationDto> getById(
            @Parameter(name = "id", description = "Идентификатор")
            @PathVariable("id") UUID id
    ) {
        OptimizationDto dto = service.getById(id);
        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "Активировать оптимизацию", description = "Возвращает оптимизацию")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Успешная активация"),
            @ApiResponse(responseCode = "404", description = "Оптимизация не найдена")
    })
    @PostMapping("/{id}/activate")
    public ResponseEntity<OptimizationDto> activate(
            @Parameter(name = "id", description = "Идентификатор")
            @PathVariable("id") UUID id
    ) {
        OptimizationDto dto = service.activate(id);
        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "Деактивировать оптимизацию", description = "Возвращает оптимизацию")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Успешная деактивация"),
            @ApiResponse(responseCode = "404", description = "Оптимизация не найдена")
    })
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<OptimizationDto> deactivate(
            @Parameter(name = "id", description = "Идентификатор")
            @PathVariable("id") UUID id
    ) {
        OptimizationDto dto = service.deactivate(id);
        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "Получить активные оптимизации", description = "Возвращает оптимизацию")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Успешное создание")
    })
    @GetMapping
    public ResponseEntity<Collection<OptimizationDto>> findAllActive() {
        List<OptimizationDto> dto = service.findAllActive();
        return ResponseEntity.ok(dto);
    }

}
