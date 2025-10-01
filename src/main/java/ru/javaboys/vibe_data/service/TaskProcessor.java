package ru.javaboys.vibe_data.service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.javaboys.vibe_data.agent.QueryOptimizerAgent;
import ru.javaboys.vibe_data.agent.tools.TrinoExplainType;
import ru.javaboys.vibe_data.domain.Task;
import ru.javaboys.vibe_data.domain.TaskInput;
import ru.javaboys.vibe_data.domain.TaskResult;
import ru.javaboys.vibe_data.domain.TaskStatus;
import ru.javaboys.vibe_data.domain.jsonb.QueryInput;
import ru.javaboys.vibe_data.domain.jsonb.TaskInputPayload;
import ru.javaboys.vibe_data.repository.TaskRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskProcessor {

    private final TrinoDbService trinoDbService;
    private final TaskRepository taskRepository;
    private final QueryOptimizerAgent optimizerAgent;

    @Transactional
    public void processTask(UUID taskId) {
        Task task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            log.warn("Задача {} не найдена для обработки", taskId);
            return;
        }
        if (task.getStatus() != TaskStatus.RUNNING) {
            log.info("Задача {} не в статусе RUNNING (текущий статус={}), пропуск", taskId, task.getStatus());
            return;
        }

        try {
            log.info("Начинаю обработку задачи {}", taskId);

            Thread.ofPlatform().name("cache-filler").start(() -> {
                runSqlCacheProcess(task);
            });

            TaskResult result = optimizerAgent.optimize(task);
            task.setResult(result);
            task.setStatus(TaskStatus.DONE);

            taskRepository.save(task);
            log.info("Задача {} успешно обработана", taskId);
        } catch (Exception e) {
            log.error("Ошибка при обработке задачи {}: {}", taskId, e.getMessage(), e);
            task.setStatus(TaskStatus.FAILED);
            task.setError(e.getMessage());
            taskRepository.save(task);
        }
    }

    private void runSqlCacheProcess(Task task) {
        List<QueryInput> queries = Optional.ofNullable(task.getInput())
                .map(TaskInput::getPayload)
                .map(TaskInputPayload::getQueries)
                .orElseGet(Collections::emptyList);
        for (QueryInput query : queries) {
            String sql = query.getQuery();
            for (TrinoExplainType type : TrinoExplainType.values()) {
                // Прогреваем кэш
                trinoDbService.explain(sql, type);
            }
        }
    }
}
