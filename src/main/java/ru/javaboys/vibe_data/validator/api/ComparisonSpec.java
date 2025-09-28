package ru.javaboys.vibe_data.validator.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ComparisonSpec {
    public enum Mode { STRICT, TOLERANCE }
    private Mode mode;
    private Double tolerancePct; // used when mode==TOLERANCE
    private String executionStrategy; // e.g., runOn=legacy|next; keep as string for now
}