package ru.javaboys.vibe_data.service;

import ru.javaboys.vibe_data.api.dto.NewTaskRequestDto;
import ru.javaboys.vibe_data.api.dto.ResultResponseDto;
import ru.javaboys.vibe_data.domain.TaskStatus;

import java.util.UUID;

public interface TaskService {
    UUID createTask(NewTaskRequestDto request);
    TaskStatus getStatus(UUID taskId);
    ResultResponseDto getResult(UUID taskId);
}
