package ru.javaboys.vibe_data.monitoring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method for execution time monitoring. All methods annotated with the same key
 * will be aggregated under that key in the statistics table.
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Monitored {
    /**
     * A required key that identifies a group of methods.
     */
    String key();
}