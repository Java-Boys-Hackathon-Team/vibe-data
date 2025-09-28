package ru.javaboys.vibe_data.agent.tools;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.javaboys.vibe_data.dto.MetricInfo;

/**
 * Инструменты для выполнения произвольных read-only SQL-запросов в Trino.
 * Предназначено для чтения служебных таблиц (например, system.runtime.nodes) и обычных SELECT-запросов.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrinoReadOnlyQueryTools {

    private final JdbcTemplate trinoJdbcTemplate;
    private final ObjectMapper objectMapper;

    @Tool(description = "Выполнить read-only SQL-запрос (SELECT/SHOW/DESCRIBE/EXPLAIN/VALUES/WITH/TABLE) в Trino и вернуть результат в JSON.")
    public MetricInfo runReadOnlyQuery(
            @ToolParam(description = "SQL-запрос только для чтения. Допустимы SELECT/SHOW/DESCRIBE/EXPLAIN/VALUES/WITH/TABLE.") String sql,
            @ToolParam(description = "Необязательно: лимит количества возвращаемых строк. Если не указан или <=0 — без явного лимита.") Integer maxRows
    ) {
        String sanitized = sanitizeSql(sql);
        if (!isReadOnly(sanitized)) {
            return MetricInfo.error("Ошибка: Допускаются только read-only запросы (SELECT/SHOW/DESCRIBE/EXPLAIN/VALUES/WITH/TABLE/WITH).");
        }
        if (containsMultipleStatements(sanitized)) {
            return MetricInfo.error("Ошибка: Поддерживается только один SQL-стейтмент за вызов (без точек с запятой).");
        }

        try {
            List<Map<String, Object>> rows = executeQuery(sanitized, maxRows != null && maxRows > 0 ? maxRows : 0);
            String json = objectToJson(rows);
            return MetricInfo.success(json);
        } catch (DataAccessException | SQLException e) {
            log.warn("Ошибка при выполнении read-only запроса в Trino", e);
            return MetricInfo.error("Ошибка при выполнении запроса: " + e.getMessage());
        }
    }

    private List<Map<String, Object>> executeQuery(String sql, int maxRows) throws SQLException {
        return trinoJdbcTemplate.execute((Connection con) -> {
            try (Statement st = con.createStatement()) {
                if (maxRows > 0) {
                    st.setMaxRows(maxRows);
                }
                try (ResultSet rs = st.executeQuery(sql)) {
                    return mapResultSet(rs);
                }
            }
        });
    }

    private List<Map<String, Object>> mapResultSet(ResultSet rs) throws SQLException {
        List<Map<String, Object>> result = new ArrayList<>();
        ResultSetMetaData md = rs.getMetaData();
        int columns = md.getColumnCount();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columns; i++) {
                String colName = md.getColumnLabel(i);
                Object value = rs.getObject(i);
                row.put(colName, value);
            }
            result.add(row);
        }
        return result;
    }

    private String objectToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "Ошибка сериализации результата в JSON: " + e.getMessage();
        }
    }

    private String sanitizeSql(String sql) {
        if (sql == null) return null;
        // Удаляем завершающие точки с запятой и пробелы/переводы строк
        return sql.replaceAll("[;\\s]+$", "").trim();
    }

    private boolean containsMultipleStatements(String sql) {
        if (sql == null) return true;
        // Если после удаления хвоста остались точки с запятой — значит больше одного стейтмента
        return sql.contains(";");
    }

    private boolean isReadOnly(String sql) {
        if (sql == null) return false;
        String s = sql.trim();
        if (s.isEmpty()) return false;
        // Разрешенные типы команд
        String upper = s.toUpperCase(Locale.ROOT);

        // Быстрый отказ по опасным ключевым словам
        if (upper.matches(".*\\b(INSERT|UPDATE|DELETE|CREATE|DROP|ALTER|TRUNCATE|MERGE|RENAME|GRANT|REVOKE|CALL|SET\\s+SESSION|RESET\\s+SESSION|COMMENT|ANALYZE\\s+TABLE|VACUUM)\\b.*")) {
            return false;
        }

        // Разрешаем начальные ключевые слова read-only
        return upper.startsWith("SELECT ")
                || upper.startsWith("WITH ") // WITH ... SELECT
                || upper.startsWith("SHOW ")
                || upper.startsWith("DESCRIBE ")
                || upper.startsWith("EXPLAIN ")
                || upper.startsWith("VALUES ")
                || upper.startsWith("TABLE ");
    }
}
