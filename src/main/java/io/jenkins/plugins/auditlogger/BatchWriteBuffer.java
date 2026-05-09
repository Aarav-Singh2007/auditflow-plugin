package io.jenkins.plugins.auditlogger;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Batch write buffer for optimized disk I/O.
 * Used internally by AuditLogStorage if batch flush is configured.
 */
public class BatchWriteBuffer {
    private static final Logger LOGGER = Logger.getLogger(BatchWriteBuffer.class.getName());

    private final ConcurrentLinkedQueue<AuditLogEntry> batch = new ConcurrentLinkedQueue<>();
    private final int batchSize;
    private final long flushIntervalMs;
    private volatile long lastFlushTime = System.currentTimeMillis();
    private final BatchFlushCallback callback;
    private final ScheduledExecutorService scheduler;

    @FunctionalInterface
    public interface BatchFlushCallback {
        void flush(List<AuditLogEntry> entries) throws Exception;
    }

    public BatchWriteBuffer(int batchSize, long flushIntervalMs, BatchFlushCallback callback) {
        this.batchSize = Math.max(1, batchSize);
        this.flushIntervalMs = Math.max(100, flushIntervalMs);
        this.callback = callback;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Audit-Batch-Flusher");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::flushIfNeeded,
                flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
    }

    public void add(AuditLogEntry entry) {
        if (entry == null) return;
        batch.offer(entry);
        if (batch.size() >= batchSize) {
            flush();
        }
    }

    private void flushIfNeeded() {
        if (!batch.isEmpty()) {
            flush();
        }
    }

    public synchronized void flush() {
        List<AuditLogEntry> entries = new ArrayList<>();
        AuditLogEntry entry;
        while ((entry = batch.poll()) != null) {
            entries.add(entry);
        }
        if (entries.isEmpty()) return;

        try {
            callback.flush(entries);
            lastFlushTime = System.currentTimeMillis();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Batch flush failed for " + entries.size() + " entries", e);
            // Re-queue only if buffer isn't already huge
            if (batch.size() < batchSize * 10) {
                entries.forEach(batch::offer);
            } else {
                LOGGER.severe("Dropping " + entries.size() + " entries to prevent OOM");
            }
        }
    }

    public int getCurrentSize() {
        return batch.size();
    }

    public void shutdown() {
        flush();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
