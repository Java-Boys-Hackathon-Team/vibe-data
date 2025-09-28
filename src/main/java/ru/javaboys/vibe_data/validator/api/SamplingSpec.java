package ru.javaboys.vibe_data.validator.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SamplingSpec {
    private Double percent; // 0..100 or 0..1 depending on caller; we will assume 0..100
    private List<LocalDate> dateRange; // [from, to]
    private List<String> partitionHints;
}