package ru.javaboys.vibe_data.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import ru.javaboys.vibe_data.llm.TrinoExplainType;

@Service
@RequiredArgsConstructor
public class TrinoMetricToolService {
    private static final String SQL_FORMAT_1 = "EXPLAIN (TYPE %s, FORMAT JSON) %s";
    private static final String SQL_FORMAT_2 = "EXPLAIN %s %s";

    private final JdbcTemplate trinoJdbcTemplate;

    public String requestExplainInJson(String sql, TrinoExplainType type) {
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
