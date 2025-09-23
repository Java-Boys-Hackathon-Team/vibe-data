package ru.javaboys.vibe_data.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import ru.javaboys.vibe_data.api.dto.DdlStatementDto;
import ru.javaboys.vibe_data.api.dto.NewTaskRequestDto;
import ru.javaboys.vibe_data.api.dto.QueryInputDto;
import ru.javaboys.vibe_data.domain.jsonb.DdlStatement;
import ru.javaboys.vibe_data.domain.jsonb.QueryInput;
import ru.javaboys.vibe_data.domain.jsonb.TaskInputPayload;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TaskInputPayloadMapper {

    TaskInputPayload toPayload(NewTaskRequestDto dto);

    DdlStatement toEntity(DdlStatementDto dto);

    QueryInput toEntity(QueryInputDto dto);

    List<DdlStatement> toDdlList(List<DdlStatementDto> list);

    List<QueryInput> toQueryInputList(List<QueryInputDto> list);
}
