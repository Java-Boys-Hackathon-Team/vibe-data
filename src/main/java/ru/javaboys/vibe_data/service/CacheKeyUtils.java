package ru.javaboys.vibe_data.service;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component("CacheKeyUtils")
public class CacheKeyUtils {

    public String normalize(String input) {
        boolean inLineComment = false;
        boolean inBlockComment = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            // Проверка на начало блочного комментария /* ... */
            if (!inLineComment && !inBlockComment && c == '/' && i + 1 < input.length() && input.charAt(i + 1) == '*') {
                inBlockComment = true;
                i++; // пропускаем '*'
                continue;
            }

            // Проверка на конец блочного комментария */
            if (inBlockComment && c == '*' && i + 1 < input.length() && input.charAt(i + 1) == '/') {
                inBlockComment = false;
                i++; // пропускаем '/'
                continue;
            }

            // Проверка на начало однострочного комментария --
            if (!inLineComment && !inBlockComment && c == '-' && i + 1 < input.length() && input.charAt(i + 1) == '-') {
                inLineComment = true;
                i++; // пропускаем второй '-'
                continue;
            }

            // Конец строки завершает однострочный комментарий
            if (inLineComment && (c == '\n' || c == '\r')) {
                inLineComment = false;
                continue;
            }

            // Если внутри комментария — игнорируем символ
            if (inLineComment || inBlockComment) {
                continue;
            }

            // Пропускаем пробелы
            if (Character.isWhitespace(c)) {
                continue;
            }

            // Добавляем в результат
            sb.append(c);
        }

        log.trace("Src query: {}", input);
        log.trace("Cache query: {}", sb);
        return sb.toString();
    }

}