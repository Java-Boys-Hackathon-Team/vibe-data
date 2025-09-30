package ru.javaboys.vibe_data.aop;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import ru.javaboys.vibe_data.monitoring.MeasurementEvent;
import ru.javaboys.vibe_data.monitoring.MethodStatsUpdater;
import ru.javaboys.vibe_data.monitoring.Monitored;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class MethodTimingAspect {

    private final MethodStatsUpdater updater;

    @Around("@annotation(monitored)")
    public Object measure(ProceedingJoinPoint pjp, Monitored monitored) throws Throwable {
        String key = monitored.key();
        long start = System.nanoTime();
        boolean success = false;
        try {
            Object result = pjp.proceed();
            success = true;
            return result;
        } catch (Throwable t) {
            throw t;
        } finally {
            long durMs = (System.nanoTime() - start) / 1_000_000L;
            boolean offered = updater.offer(new MeasurementEvent(key, durMs, success));
            if (!offered) {
                log.debug("MethodTimingAspect queue full, dropping measurement for key={} durMs={} success={}", key, durMs, success);
            }
        }
    }
}
