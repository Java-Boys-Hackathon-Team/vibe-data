package ru.javaboys.vibe_data.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.javaboys.vibe_data.domain.TaskResult;

import java.util.UUID;

public interface TaskResultRepository extends JpaRepository<TaskResult, UUID> {
}
