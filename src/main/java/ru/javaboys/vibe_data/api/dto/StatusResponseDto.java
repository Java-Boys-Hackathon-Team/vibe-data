package ru.javaboys.vibe_data.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.javaboys.vibe_data.domain.TaskStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Статус задачи")
public class StatusResponseDto {
    @Schema(description = "Статус")
    private TaskStatus status;
}
