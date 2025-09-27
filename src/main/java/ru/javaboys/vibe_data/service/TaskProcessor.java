package ru.javaboys.vibe_data.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.javaboys.vibe_data.agent.QueryOptimizerAgent;
import ru.javaboys.vibe_data.domain.Task;
import ru.javaboys.vibe_data.domain.TaskResult;
import ru.javaboys.vibe_data.domain.TaskStatus;
import ru.javaboys.vibe_data.repository.TaskRepository;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskProcessor {

    private final TaskRepository taskRepository;
    private final QueryOptimizerAgent optimizerAgent;

    @Transactional
    public void processTask(UUID taskId) {
        Task task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            log.warn("Task {} not found for processing", taskId);
            return;
        }
        if (task.getStatus() != TaskStatus.RUNNING) {
            log.info("Task {} is not RUNNING (status={}), skip", taskId, task.getStatus());
            return;
        }

        try {
            log.info("Start processing task {}", taskId);

            TaskResult result = optimizerAgent.optimize(task);
            task.setResult(result);
            task.setStatus(TaskStatus.DONE);

            taskRepository.save(task);
            log.info("Task {} processed successfully", taskId);
        } catch (Exception e) {
            log.error("Task {} failed: {}", taskId, e.getMessage(), e);
            task.setStatus(TaskStatus.FAILED);
            task.setError(e.getMessage());
            taskRepository.save(task);
        }
    }
}
