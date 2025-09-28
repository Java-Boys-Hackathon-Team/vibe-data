package ru.javaboys.vibe_data.aop;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ToolInvocationLoggingAspect {

    private static final int MAX_LOG_LEN = 4000;

    // Можно заинжектить общий ObjectMapper из контекста
    private final ObjectMapper objectMapper;

    /**
     * Перехватываем все методы внутри пакета tools, помеченные @Tool (Spring AI).
     * Важно: точка среза использует и пакет, и аннотацию, чтобы не зацепить "лишнее".
     */
    @Around("within(ru.javaboys.vibe_data.agent.tools..*) && @annotation(org.springframework.ai.tool.annotation.Tool)")
    public Object logToolCall(ProceedingJoinPoint pjp) throws Throwable {
        String toolClass = pjp.getTarget().getClass().getSimpleName();
        String method = pjp.getSignature().getName();
        String toolName = toolClass + "." + method;

        String argsJson = toJsonSafe(pjp.getArgs());
        log.info("[TOOL_CALL] name={}, args={}", toolName, truncate(argsJson));

        long started = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            long tookMs = System.currentTimeMillis() - started;
            String resultStr = stringifyResult(result);

            log.info("[TOOL_OK] name={}, tookMs={}, result={}", toolName, tookMs, truncate(resultStr));
            return result;
        } catch (Throwable t) {
            long tookMs = System.currentTimeMillis() - started;
            log.warn("[TOOL_ERR] name={}, tookMs={}, errorClass={}, message={}",
                    toolName, tookMs, t.getClass().getSimpleName(), truncate(t.getMessage()));
            throw t;
        }
    }

    private String stringifyResult(Object result) {
        if (result == null) return "null";
        // Если tool возвращает String (как у вас), логируем как есть; иначе — JSON
        if (result instanceof String s) return s;
        return toJsonSafe(result);
    }

    private String toJsonSafe(Object obj) {
        try {
            // Маскируем возможные секреты в простых структурах
            Object masked = maskSecrets(obj);
            return objectMapper.writeValueAsString(masked);
        } catch (JsonProcessingException e) {
            return String.valueOf(obj);
        }
    }

    private Object maskSecrets(Object obj) {
        if (obj instanceof Object[] arr) {
            Object[] copy = new Object[arr.length];
            for (int i = 0; i < arr.length; i++) copy[i] = maskSecrets(arr[i]);
            return copy;
        }
        if (obj instanceof Map<?, ?> in) {
            Map<Object,Object> copy = new HashMap<>();
            for (Map.Entry<?,?> e : in.entrySet()) {
                Object k = e.getKey();
                Object v = e.getValue();
                if (k instanceof String key && isSecretKey(key)) {
                    copy.put(k, "***");
                } else {
                    copy.put(k, maskSecrets(v));
                }
            }
            return copy;
        }
        return obj;
    }

    private boolean isSecretKey(String key) {
        String k = key.toLowerCase();
        return k.contains("password") || k.contains("secret") || k.contains("token") || k.contains("apikey");
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() <= MAX_LOG_LEN ? s : s.substring(0, MAX_LOG_LEN) + "...[truncated]";
        // при необходимости сюда можно добавить более «умную» обрезку многострочных JSON
    }
}
