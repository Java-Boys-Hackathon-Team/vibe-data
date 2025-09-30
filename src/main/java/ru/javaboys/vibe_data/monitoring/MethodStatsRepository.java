package ru.javaboys.vibe_data.monitoring;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MethodStatsRepository extends JpaRepository<MethodStats, UUID> {
    Optional<MethodStats> findByKey(String key);
}