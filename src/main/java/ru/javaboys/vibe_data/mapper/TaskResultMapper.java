package ru.javaboys.vibe_data.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import ru.javaboys.vibe_data.api.dto.ResultResponseDto;
import ru.javaboys.vibe_data.api.dto.RewrittenQueryDto;
import ru.javaboys.vibe_data.api.dto.SqlBlockDto;
import ru.javaboys.vibe_data.domain.TaskResult;
import ru.javaboys.vibe_data.domain.jsonb.RewrittenQuery;
import ru.javaboys.vibe_data.domain.jsonb.SqlBlock;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TaskResultMapper {

    ResultResponseDto toDto(TaskResult entity);

    SqlBlockDto toDto(SqlBlock entity);

    RewrittenQueryDto toDto(RewrittenQuery entity);

    List<SqlBlockDto> toSqlBlockDtoList(List<SqlBlock> list);

    List<RewrittenQueryDto> toRewrittenQueryDtoList(List<RewrittenQuery> list);
}
