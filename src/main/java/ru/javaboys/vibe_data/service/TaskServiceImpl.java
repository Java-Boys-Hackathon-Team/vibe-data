package ru.javaboys.vibe_data.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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


@Slf4j
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
        log.info("Начато создание задачи: ddl={}, queries={}",
                request.getDdl() != null ? request.getDdl().size() : 0,
                request.getQueries() != null ? request.getQueries().size() : 0);

        Task task = Task.builder()
                .status(TaskStatus.RUNNING)
                .build();

        TaskInput input = TaskInput.builder()
                .task(task)
                .payload(taskInputPayloadMapper.toPayload(request))
                .build();

        task.setInput(input);

        Task saved = taskRepository.save(task);
        log.info("Задача сохранена в БД, id={}", saved.getId());

        events.publishEvent(new TaskCreatedEvent(saved.getId()));
        log.info("Опубликовано событие TaskCreatedEvent для задачи id={}", saved.getId());

        return saved.getId();
    }

    @Override
    public TaskStatus getStatus(UUID taskId) {
        log.info("Запрошен статус задачи id={}", taskId);
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> {
                    log.error("Задача id={} не найдена", taskId);
                    return new ResponseStatusException(NOT_FOUND, "Задача не найдена");
                });
        TaskStatus status = task.getStatus();
        log.info("Статус задачи id={} = {}", taskId, status);
        return status;
    }

    @Override
    public ResultResponseDto getResult(UUID taskId) {
        log.info("Запрошен результат задачи id={}", taskId);
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> {
                    log.error("Задача id={} не найдена", taskId);
                    return new ResponseStatusException(NOT_FOUND, "Задача не найдена");
                });
        TaskResult result = task.getResult();
        if (result == null) {
            log.error("Результат для задачи id={} не найден (ещё не готов)", taskId);
            throw new ResponseStatusException(NOT_FOUND, "Результат не найден");
        }
        ResultResponseDto dto = taskResultMapper.toDto(result);
        log.info("Результат для задачи id={} получен и подготовлен к выдаче", taskId);
        return dto;
    }
}
