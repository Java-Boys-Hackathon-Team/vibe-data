package ru.javaboys.vibe_data.agent;

import java.util.List;

public record PerQueryOptimizationOutput(
        String queryid,
        String rewrittenQuery,
        List<String> ddlChanges
) {}
