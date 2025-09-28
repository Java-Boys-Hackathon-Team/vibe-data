package ru.javaboys.vibe_data.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.javaboys.vibe_data.domain.Optimization;

public interface OptimizationRepository extends JpaRepository<Optimization, UUID> {

    List<Optimization> findAllByActiveIsTrue();

}
