package ru.javaboys.vibe_data.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.javaboys.vibe_data.agent.tools.TrinoExplainType;
import ru.javaboys.vibe_data.config.TrinoDatasourceConfiguration;
import ru.javaboys.vibe_data.domain.Task;
import ru.javaboys.vibe_data.domain.jsonb.QueryInput;
import ru.javaboys.vibe_data.domain.jsonb.TaskInputPayload;
import ru.javaboys.vibe_data.dto.TrinoResponse;
import ru.javaboys.vibe_data.repository.TaskRepository;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrinoDbService {
    private static final String SQL_FORMAT_1 = "EXPLAIN (TYPE %s, FORMAT JSON) %s";
    private static final String SQL_FORMAT_2 = "EXPLAIN %s %s";
    private static final String CACHE_NAME = "trino-db-explain";

    private final TaskRepository taskRepository;

    @Cacheable(
            value = CACHE_NAME,
            key = "#queryid + ':' + #type",
            unless = "#result == null || #result.response == null"
    )
    public TrinoResponse explain(UUID taskId, String queryid, TrinoExplainType type) {
        log.info("Executing explain request for type: {} and queryid: {} (taskId={})", type, queryid, taskId);
        try {
            Task task = taskRepository.findById(taskId).orElse(null);
            if (task == null || task.getInput() == null || task.getInput().getPayload() == null) {
                log.warn("Не удалось найти задачу или входные данные для taskId={}", taskId);
                return TrinoResponse.error();
            }

            TaskInputPayload payload = task.getInput().getPayload();
            String url = payload.getUrl();
            if (url == null || url.isBlank()) {
                log.warn("URL Trino пуст для taskId={}", taskId);
                return TrinoResponse.error();
            }

            String sql = null;
            List<QueryInput> queries = payload.getQueries();
            if (queries != null) {
                for (QueryInput qi : queries) {
                    if (qi != null && queryid != null && queryid.equals(qi.getQueryid())) {
                        sql = qi.getQuery();
                        break;
                    }
                }
            }
            if (sql == null || sql.isBlank()) {
                log.warn("SQL не найден по queryid={} для taskId={}", queryid, taskId);
                return TrinoResponse.error();
            }

            String explain = requestExplainInJsonInternal(url, sql, type);
            return TrinoResponse.success(explain);
        } catch (DataAccessException e) {
            log.warn("Ошибка при выполнении read-only запроса в Trino", e);
            return TrinoResponse.error();
        }
    }

    private String requestExplainInJsonInternal(String url, String sql, TrinoExplainType type) {
        JdbcTemplate template = TrinoDatasourceConfiguration.templateForUrl(url);

        if (type == TrinoExplainType.ANALYZE || type == TrinoExplainType.ANALYZE_VERBOSE) {
            return template.queryForObject(
                    String.format(SQL_FORMAT_2, type.getName(), sanitizeSql(sql)),
                    String.class
            );
        }

        return template.queryForObject(
                String.format(SQL_FORMAT_1, type.getName(), sanitizeSql(sql)),
                String.class
        );
    }

    private String sanitizeSql(String sql) {
        if (sql == null) return null;
        // Remove trailing semicolons and whitespace, which Trino parser doesn't accept before EOF
        return sql.replaceAll("[;\\s]+$", "");
    }
}
