package ru.javaboys.vibe_data.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import ru.javaboys.vibe_data.domain.converter.RewrittenQueryListJsonConverter;
import ru.javaboys.vibe_data.domain.converter.SqlBlockListJsonConverter;
import ru.javaboys.vibe_data.domain.jsonb.RewrittenQuery;
import ru.javaboys.vibe_data.domain.jsonb.SqlBlock;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"task"})
@Entity
@Table(name = "task_result")
public class TaskResult extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false, unique = true)
    private Task task;

    @Valid
    @Convert(converter = SqlBlockListJsonConverter.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<SqlBlock> ddl;

    @Valid
    @Convert(converter = SqlBlockListJsonConverter.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<SqlBlock> migrations;

    @Valid
    @Convert(converter = RewrittenQueryListJsonConverter.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<RewrittenQuery> queries;
}
