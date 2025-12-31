package dev.mccli.util;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Captures log messages for retrieval via the logs command.
 *
 * This is a ring buffer that keeps the most recent N log entries.
 * Can be filtered by level and regex pattern.
 *
 * Note: This requires integration with the logging framework.
 * For now, we provide a simple API that can be called from
 * a custom Log4j appender or similar.
 */
public class LogCapture {
    private static final int MAX_ENTRIES = 1000;
    private static final Deque<LogEntry> entries = new ConcurrentLinkedDeque<>();
    private static final AtomicLong NEXT_ID = new AtomicLong(0);

    private static final Map<String, Integer> LEVEL_PRIORITY = Map.of(
        "error", 0,
        "warn", 1,
        "info", 2,
        "debug", 3
    );

    /**
     * Add a log entry. Called from logging integration.
     */
    public static void addEntry(String level, String logger, String message) {
        LogEntry entry = new LogEntry(
            NEXT_ID.incrementAndGet(),
            Instant.now().toString(),
            level.toLowerCase(),
            logger,
            message
        );

        entries.addLast(entry);

        // Trim to max size
        while (entries.size() > MAX_ENTRIES) {
            entries.removeFirst();
        }
    }

    /**
     * Get recent log entries matching criteria.
     */
    public static List<LogEntry> getRecentLogs(String minLevel, int limit, String filterPattern, long sinceId) {
        int minPriority = LEVEL_PRIORITY.getOrDefault(minLevel.toLowerCase(), 2);
        Pattern pattern = filterPattern != null ? Pattern.compile(filterPattern, Pattern.CASE_INSENSITIVE) : null;

        return entries.stream()
            .filter(e -> LEVEL_PRIORITY.getOrDefault(e.level(), 2) <= minPriority)
            .filter(e -> pattern == null || pattern.matcher(e.message()).find() || pattern.matcher(e.logger()).find())
            .filter(e -> sinceId <= 0 || e.id() > sinceId)
            .collect(Collectors.collectingAndThen(
                Collectors.toList(),
                list -> {
                    // Get last N entries
                    int start = Math.max(0, list.size() - limit);
                    return list.subList(start, list.size());
                }
            ));
    }

    /**
     * Clear all captured logs.
     */
    public static void clear() {
        entries.clear();
    }

    /**
     * Get count of captured logs.
     */
    public static int getCount() {
        return entries.size();
    }

    public static long getLastId() {
        return NEXT_ID.get();
    }

    public record LogEntry(long id, String timestamp, String level, String logger, String message) {}
}
