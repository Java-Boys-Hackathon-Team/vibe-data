package ru.javaboys.vibe_data.validator.dto;

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
    private Double percent;
    private List<LocalDate> dateRange;
    private List<String> partitionHints;
}