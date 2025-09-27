package ru.javaboys.vibe_data.agent;

import java.util.List;

public record FinalMigrationOutput(
        List<String> newDdl,
        List<String> migrations
) {}