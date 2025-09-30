package ru.javaboys.vibe_data.monitoring;

/**
 * Represents a single method execution measurement.
 */
public record MeasurementEvent(String key, long durationMs, boolean success) { }