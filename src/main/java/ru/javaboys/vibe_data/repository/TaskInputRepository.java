package ru.javaboys.vibe_data.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.javaboys.vibe_data.domain.TaskInput;

import java.util.UUID;

public interface TaskInputRepository extends JpaRepository<TaskInput, UUID> {
}
