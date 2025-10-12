package ru.javaboys.vibe_data.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.javaboys.vibe_data.agent.tools.TrinoExplainType;
import ru.javaboys.vibe_data.domain.jsonb.QueryInput;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SqlCacheWarmupService {

    private final TrinoDbService trinoDbService;

    /**
     * Прогревает кеш explain-запросов для переданного упорядоченного списка запросов.
     * Метод устойчив к null и пустым спискам.
     */
    public void runSqlCacheProcess(List<QueryInput> queryInputList) {
        List<QueryInput> queries = queryInputList == null ? Collections.emptyList() : queryInputList;
        if (queries.isEmpty()) {
            log.info("Прогрев кэша: список запросов пуст — пропуск");
            return;
        }
        log.info("Старт прогрева кэша explain для {} запросов", queries.size());
        for (QueryInput query : queries) {
            String sql = query.getQuery();
            for (TrinoExplainType type : TrinoExplainType.values()) {
                try {
                    trinoDbService.explain(sql, type);
                } catch (Exception e) {
                    // Не прерываем прогрев при ошибке одного из explain
                    log.warn("Ошибка прогрева кэша для type={} queryId={}: {}", type, query.getQueryid(), e.getMessage());
                }
            }
        }
        log.info("Прогрев кэша explain завершён");
    }
}
