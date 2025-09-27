package ru.javaboys.vibe_data.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.javaboys.vibe_data.api.dto.NewTaskRequestDto;
import ru.javaboys.vibe_data.api.dto.ResultResponseDto;
import ru.javaboys.vibe_data.domain.Task;
import ru.javaboys.vibe_data.domain.TaskInput;
import ru.javaboys.vibe_data.domain.TaskResult;
import ru.javaboys.vibe_data.domain.TaskStatus;
import ru.javaboys.vibe_data.event.TaskCreatedEvent;
import ru.javaboys.vibe_data.mapper.TaskInputPayloadMapper;
import ru.javaboys.vibe_data.mapper.TaskResultMapper;
import ru.javaboys.vibe_data.repository.TaskRepository;

import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;


@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final TaskInputPayloadMapper taskInputPayloadMapper;
    private final TaskResultMapper taskResultMapper;
    private final ApplicationEventPublisher events;

    @Override
    @Transactional
    public UUID createTask(NewTaskRequestDto request) {
        Task task = Task.builder()
                .status(TaskStatus.RUNNING)
                .build();

        TaskInput input = TaskInput.builder()
                .task(task)
                .payload(taskInputPayloadMapper.toPayload(request))
                .build();

        task.setInput(input);

        Task saved = taskRepository.save(task);

        events.publishEvent(new TaskCreatedEvent(saved.getId()));

        return saved.getId();
    }

    @Override
    public TaskStatus getStatus(UUID taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Task not found"));
        return task.getStatus();
    }

    @Override
    public ResultResponseDto getResult(UUID taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Task not found"));
        TaskResult result = task.getResult();
        if (result == null) {
            throw new ResponseStatusException(NOT_FOUND, "Result not found");
        }
        return taskResultMapper.toDto(result);
    }
}
