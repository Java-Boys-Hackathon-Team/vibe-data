package ru.javaboys.vibe_data.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.javaboys.vibe_data.api.dto.NewTaskRequestDto;
import ru.javaboys.vibe_data.api.dto.NewTaskResponseDto;
import ru.javaboys.vibe_data.api.dto.ResultResponseDto;
import ru.javaboys.vibe_data.api.dto.StatusResponseDto;
import ru.javaboys.vibe_data.config.LlmProperties;
import ru.javaboys.vibe_data.domain.TaskStatus;
import ru.javaboys.vibe_data.service.TaskService;

@Slf4j
@RestController
@RequestMapping
@RequiredArgsConstructor
@Tag(name = "Задачи")
public class TaskController {

    private final LlmProperties llmProperties;
    private final TaskService taskService;

    @Operation(summary = "Создать новую задачу", description = "Возвращает данные созданной задачи")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Успешное создание")
    })
    @PostMapping("/new")
    public ResponseEntity<NewTaskResponseDto> createTask(@Valid @RequestBody NewTaskRequestDto request) {
        log.info("Принят запрос на создание новой задачи");

        if (request.getLlmModel() != null
            && !llmProperties.getValidModels().contains(request.getLlmModel())) {
            throw new IllegalArgumentException("Invalid model name, valid model names: " + llmProperties.getValidModels());
        }

        try {
            UUID id = taskService.createTask(request);
            log.info("Задача создана, id={}", id);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(NewTaskResponseDto.builder().taskid(id).build());
        } catch (Exception e) {
            log.error("Ошибка при создании задачи: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Operation(summary = "Статус задачи", description = "Возвращает статус задачи")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Успешное получение данных"),
            @ApiResponse(responseCode = "404", description = "Задача не найдена, либо не завершена")
    })
    @GetMapping("/status")
    public ResponseEntity<StatusResponseDto> getStatus(
            @Parameter(name = "task_id", description = "Идентификатор задачи")
            @RequestParam("task_id") UUID taskId
    ) {
        TaskStatus status = taskService.getStatus(taskId);
        return ResponseEntity.ok(StatusResponseDto.builder().status(status).build());
    }

    @Operation(summary = "Результат задачи", description = "Возвращает результаты задачи")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Успешное получение данных"),
            @ApiResponse(responseCode = "404", description = "Задача не найдена, либо не завершена")
    })
    @GetMapping("/getresult")
    public ResponseEntity<ResultResponseDto> getResult(
            @Parameter(name = "task_id", description = "Идентификатор задачи")
            @RequestParam("task_id") UUID taskId
    ) {
        ResultResponseDto result = taskService.getResult(taskId);
        return ResponseEntity.ok(result);
    }
}
