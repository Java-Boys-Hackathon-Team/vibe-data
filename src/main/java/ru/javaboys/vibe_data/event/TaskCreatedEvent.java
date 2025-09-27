package ru.javaboys.vibe_data.event;


import java.util.UUID;

public record TaskCreatedEvent(UUID taskId) {}
