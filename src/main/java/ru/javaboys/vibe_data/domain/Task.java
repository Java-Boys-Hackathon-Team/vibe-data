package ru.javaboys.vibe_data.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"input", "result"})
@Entity
@Table(name = "tasks",
       indexes = {
           @Index(name = "idx_tasks_status", columnList = "status"),
           @Index(name = "idx_tasks_created_at", columnList = "created_at")
       })
public class Task extends BaseEntity {

    @Column(name = "llm_model", columnDefinition = "VARCHAR(64)")
    private String llmModel;

    @Column(name = "temperature", columnDefinition = "DOUBLE")
    private Double temperature;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskStatus status;

    @Column(columnDefinition = "text")
    private String error;

    @OneToOne(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true, optional = false)
    private TaskInput input;

    @OneToOne(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    private TaskResult result;
}
