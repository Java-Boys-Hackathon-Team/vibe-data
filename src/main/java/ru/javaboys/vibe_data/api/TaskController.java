package ru.javaboys.vibe_data.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.javaboys.vibe_data.api.dto.NewTaskRequestDto;
import ru.javaboys.vibe_data.api.dto.NewTaskResponseDto;
import ru.javaboys.vibe_data.api.dto.ResultResponseDto;
import ru.javaboys.vibe_data.api.dto.StatusResponseDto;
import ru.javaboys.vibe_data.domain.TaskStatus;
import ru.javaboys.vibe_data.service.TaskService;

import java.util.UUID;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping("/new")
    public ResponseEntity<NewTaskResponseDto> createTask(@Valid @RequestBody NewTaskRequestDto request) {
        UUID id = taskService.createTask(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(NewTaskResponseDto.builder().taskid(id).build());
    }

    @GetMapping("/status")
    public ResponseEntity<StatusResponseDto> getStatus(@RequestParam("task_id") UUID taskId) {
        TaskStatus status = taskService.getStatus(taskId);
        return ResponseEntity.ok(StatusResponseDto.builder().status(status).build());
    }

    @GetMapping("/getresult")
    public ResponseEntity<ResultResponseDto> getResult(@RequestParam("task_id") UUID taskId) {
        ResultResponseDto result = taskService.getResult(taskId);
        return ResponseEntity.ok(result);
    }
}
