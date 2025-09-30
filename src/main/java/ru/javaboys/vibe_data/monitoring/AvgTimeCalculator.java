package ru.javaboys.vibe_data.monitoring;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encapsulates the formula for computing the new average time.
 * Default: Exponential Weighted Moving Average (EWMA) which is resilient to spikes.
 */
@Component
public class AvgTimeCalculator {

    /**
     * Smoothing factor for EWMA in range (0,1]. Larger alpha gives more weight to recent samples.
     */
    private final double alpha;

    public AvgTimeCalculator(@Value("${monitoring.ewma.alpha:0.2}") double alpha) {
        if (alpha <= 0 || alpha > 1) {
            throw new IllegalArgumentException("monitoring.ewma.alpha must be in (0,1]");
        }
        this.alpha = alpha;
    }

    public double getAlpha() {
        return alpha;
    }

    /**
     * Computes new average from previous average and new sample using EWMA.
     * If prevAvg is null or zero (no data), we start from the sample value.
     */
    public double ewma(Double prevAvg, double sampleMs) {
        if (prevAvg == null || prevAvg == 0d) {
            return sampleMs;
        }
        return alpha * sampleMs + (1.0 - alpha) * prevAvg;
    }
}
