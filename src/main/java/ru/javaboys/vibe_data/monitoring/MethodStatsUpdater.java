package ru.javaboys.vibe_data.monitoring;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A single-writer background service that persists measurement events to DB.
 * Uses a bounded, thread-safe queue to decouple producers (aspects) from the DB and
 * to avoid data races.
 */
@Slf4j
@Service
public class MethodStatsUpdater {

    private final MethodStatsRepository repository;
    private final AvgTimeCalculator avgTimeCalculator;

    private Thread worker;
    private volatile boolean running = false;

    private final BlockingQueue<MeasurementEvent> queue;

    public MethodStatsUpdater(
            MethodStatsRepository repository,
            AvgTimeCalculator avgTimeCalculator,
            @Value("${monitoring.queue.capacity:10000}") int capacity
    ) {
        this.repository = repository;
        this.avgTimeCalculator = avgTimeCalculator;
        this.queue = new LinkedBlockingQueue<>(capacity);
    }

    @PostConstruct
    public void start() {
        running = true;
        worker = new Thread(this::runLoop, "method-stats-updater");
        worker.setDaemon(true);
        worker.start();
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
            try { worker.join(Duration.ofSeconds(2).toMillis()); } catch (InterruptedException ignored) {}
        }
    }

    public boolean offer(MeasurementEvent ev) {
        return queue.offer(ev);
    }

    private void runLoop() {
        final int maxBatch = 500;
        final long pollTimeoutMs = 1000;
        List<MeasurementEvent> batch = new ArrayList<>(maxBatch);
        while (running) {
            try {
                MeasurementEvent first = queue.poll(pollTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue; // timeout
                }
                batch.clear();
                batch.add(first);
                queue.drainTo(batch, maxBatch - 1);
                persistBatch(batch);
            } catch (InterruptedException e) {
                // shutdown or spurious
            } catch (Throwable t) {
                log.warn("MethodStatsUpdater failed to persist batch: {}", t.toString());
            }
        }
        // drain remaining
        List<MeasurementEvent> rest = new ArrayList<>();
        queue.drainTo(rest);
        if (!rest.isEmpty()) {
            try {
                persistBatch(rest);
            } catch (Throwable t) {
                log.warn("MethodStatsUpdater failed to persist remaining: {}", t.toString());
            }
        }
    }

    private void persistBatch(List<MeasurementEvent> events) {
        // group by key
        Map<String, List<MeasurementEvent>> byKey = new HashMap<>();
        for (MeasurementEvent e : events) {
            byKey.computeIfAbsent(e.key(), k -> new ArrayList<>()).add(e);
        }
        for (Map.Entry<String, List<MeasurementEvent>> entry : byKey.entrySet()) {
            String key = entry.getKey();
            List<MeasurementEvent> list = entry.getValue();

            MethodStats stats = repository.findByKey(key).orElseGet(() -> {
                MethodStats ms = new MethodStats();
                ms.setKey(key);
                ms.setAvgTimeMs(0d);
                ms.setTotalCount(0L);
                ms.setSuccessCount(0L);
                ms.setErrorCount(0L);
                return ms;
            });

            double avg = stats.getAvgTimeMs() == null ? 0d : stats.getAvgTimeMs();
            long total = stats.getTotalCount() == null ? 0L : stats.getTotalCount();
            long succ = stats.getSuccessCount() == null ? 0L : stats.getSuccessCount();
            long err = stats.getErrorCount() == null ? 0L : stats.getErrorCount();

            for (MeasurementEvent e : list) {
                avg = avgTimeCalculator.ewma(avg, e.durationMs());
                total += 1;
                if (e.success()) succ += 1; else err += 1;
            }

            stats.setAvgTimeMs(avg);
            stats.setTotalCount(total);
            stats.setSuccessCount(succ);
            stats.setErrorCount(err);
            repository.save(stats);
        }
    }
}
