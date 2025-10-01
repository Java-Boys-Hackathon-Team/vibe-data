package ru.javaboys.vibe_data.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.javaboys.vibe_data.domain.Task;
import ru.javaboys.vibe_data.domain.TaskStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findByStatus(TaskStatus status);

    Optional<Task> findByIdAndStatusIs(UUID id, TaskStatus status);
}
