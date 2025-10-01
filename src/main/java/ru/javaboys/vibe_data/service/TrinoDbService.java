package ru.javaboys.vibe_data.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.javaboys.vibe_data.agent.tools.TrinoExplainType;
import ru.javaboys.vibe_data.dto.TrinoResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrinoDbService {
    private static final String SQL_FORMAT_1 = "EXPLAIN (TYPE %s, FORMAT JSON) %s";
    private static final String SQL_FORMAT_2 = "EXPLAIN %s %s";
    private static final String CACHE_NAME = "trino-db-explain-cache";

    private final JdbcTemplate trinoJdbcTemplate;

    @Cacheable(
            value = CACHE_NAME,
            key = "{#sql, #type}",
            unless = "#result == null || #result.response == null"
    )
    public TrinoResponse explain(String sql, TrinoExplainType type) {
        log.info("Executing explain request for type: {} and SQL: {}", type, sql);
        try {
            String explain = requestExplainInJsonInternal(sql, type);
            return TrinoResponse.success(explain);
        } catch (DataAccessException e) {
            log.warn("Ошибка при выполнении read-only запроса в Trino", e);
            return TrinoResponse.error();
        }
    }

    private String requestExplainInJsonInternal(String sql, TrinoExplainType type) {
        if (type == TrinoExplainType.ANALYZE || type == TrinoExplainType.ANALYZE_VERBOSE) {
            return trinoJdbcTemplate.queryForObject(
                    String.format(SQL_FORMAT_2, type.getName(), sanitizeSql(sql)),
                    String.class
            );
        }

        return trinoJdbcTemplate.queryForObject(
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
