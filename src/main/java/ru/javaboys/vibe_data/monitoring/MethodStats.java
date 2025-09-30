package ru.javaboys.vibe_data.monitoring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.javaboys.vibe_data.domain.BaseEntity;

/**
 * Aggregated statistics per monitoring key.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "method_stats", indexes = {
        @Index(name = "ux_method_stats_key", columnList = "stats_key", unique = true)
})
public class MethodStats extends BaseEntity {

    @Column(name = "stats_key", nullable = false, unique = true, length = 255)
    private String key;

    /**
     * Exponential moving average of execution time in milliseconds.
     */
    @Column(name = "avg_time_ms", nullable = false)
    private Double avgTimeMs = 0d;

    @Column(name = "total_count", nullable = false)
    private Long totalCount = 0L;

    @Column(name = "success_count", nullable = false)
    private Long successCount = 0L;

    @Column(name = "error_count", nullable = false)
    private Long errorCount = 0L;
}
