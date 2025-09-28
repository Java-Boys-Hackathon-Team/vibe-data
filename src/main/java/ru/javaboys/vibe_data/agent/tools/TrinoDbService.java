package ru.javaboys.vibe_data.agent.tools;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.javaboys.vibe_data.dto.TrinoResponse;

@Service
@RequiredArgsConstructor
public class TrinoDbService {
    private static final String SQL_FORMAT_1 = "EXPLAIN (TYPE %s, FORMAT JSON) %s";
    private static final String SQL_FORMAT_2 = "EXPLAIN %s %s";

    private final JdbcTemplate trinoJdbcTemplate;

    public TrinoResponse explain(String sql, TrinoExplainType type) {
        try {
            String explain = requestExplainInJsonInternal(sql, type);
            return TrinoResponse.success(explain);
        } catch (DataAccessException e) {
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
