package ru.javaboys.vibe_data.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.javaboys.vibe_data.domain.TaskStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusResponseDto {
    private TaskStatus status;
}
