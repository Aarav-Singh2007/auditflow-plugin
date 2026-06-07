package io.jenkins.plugins.auditlogger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight anomaly detection for dashboard alerts.
 *
 * Failed-login detection is intentionally simple, bounded, and configurable so
 * it can run safely on the audit ingest path without accumulating unbounded
 * state.
 */
public class AnomalyDetector {
    private static final int DEFAULT_FAILED_LOGIN_THRESHOLD = 5;
    private static final int DEFAULT_FAILED_LOGIN_WINDOW_MINUTES = 15;
    private static final int MAX_TRACKED_USERS = 10_000;
    private static final int MAX_ALERTS = 1_000;
    private static final long ALERT_RETENTION_MS = 24L * 60L * 60L * 1000L;
    private static final long CLEANUP_INTERVAL_MS = 60L * 1000L;

    public enum AnomalyType {
        BRUTE_FORCE_LOGIN, UNUSUAL_IP, MASS_CHANGES,
        AFTER_HOURS_ADMIN, CREDENTIAL_EXPOSURE
    }

    public static class AnomalyAlert {
        public final AnomalyType type;
        public final String user;
        public final String details;
        public final long timestamp;
        public final String severity;

        public AnomalyAlert(AnomalyType type, String user, String details, String severity) {
            this(type, user, details, severity, System.currentTimeMillis());
        }

        AnomalyAlert(AnomalyType type, String user, String details, String severity, long timestamp) {
            this.type = type;
            this.user = user;
            this.details = details;
            this.timestamp = timestamp;
            this.severity = severity;
        }
    }

    private static final class FailedLoginWindow {
        private final ArrayDeque<Long> timestamps = new ArrayDeque<>();
        private long lastObserved;
    }

    private final ConcurrentHashMap<String, FailedLoginWindow> failedLogins = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<AnomalyAlert> activeAlerts = new CopyOnWriteArrayList<>();
    private final AtomicLong lastCleanup = new AtomicLong(0L);

    public void analyze(AuditLogEntry entry) {
        analyze(entry, AuditLoggerConfiguration.get());
    }

    public void analyze(AuditLogEntry entry, AuditLoggerConfiguration config) {
        if (entry == null || !"FAILED_LOGIN".equals(entry.getAction())) {
            return;
        }

        boolean detectionEnabled = config == null || config.isAnomalyFailedLogins();
        if (!detectionEnabled) {
            maybeCleanup(entry.getTimestamp(),
                    config != null ? config.getAnomalyFailedLoginsWindowMinutes() : DEFAULT_FAILED_LOGIN_WINDOW_MINUTES);
            return;
        }

        int threshold = config != null
                ? config.getAnomalyFailedLoginsThreshold()
                : DEFAULT_FAILED_LOGIN_THRESHOLD;
        int windowMinutes = config != null
                ? config.getAnomalyFailedLoginsWindowMinutes()
                : DEFAULT_FAILED_LOGIN_WINDOW_MINUTES;

        threshold = Math.max(2, threshold);
        windowMinutes = Math.max(1, windowMinutes);

        String user = entry.getUsername() != null ? entry.getUsername() : "UNKNOWN";
        long eventTime = entry.getTimestamp();
        long cutoff = eventTime - windowMinutes * 60_000L;

        FailedLoginWindow window = failedLogins.computeIfAbsent(user, ignored -> new FailedLoginWindow());
        int recentFailures;
        synchronized (window) {
            window.lastObserved = eventTime;
            while (!window.timestamps.isEmpty() && window.timestamps.peekFirst() <= cutoff) {
                window.timestamps.removeFirst();
            }
            window.timestamps.addLast(eventTime);
            recentFailures = window.timestamps.size();
            if (recentFailures >= threshold) {
                activeAlerts.add(new AnomalyAlert(
                        AnomalyType.BRUTE_FORCE_LOGIN,
                        user,
                        "User failed to log in " + recentFailures + " times in "
                                + windowMinutes + " minute" + (windowMinutes == 1 ? "" : "s") + ".",
                        "CRITICAL"));
                trimAlerts();
                window.timestamps.clear();
            }
        }

        maybeCleanup(eventTime, windowMinutes);
    }

    public List<AnomalyAlert> getAlerts(int limit) {
        if (limit <= 0 || activeAlerts.isEmpty()) {
            return Collections.emptyList();
        }

        int size = activeAlerts.size();
        int start = Math.max(0, size - limit);
        List<AnomalyAlert> result = new ArrayList<>(size - start);
        for (int i = start; i < size; i++) {
            result.add(activeAlerts.get(i));
        }
        return result;
    }

    public void cleanupOldAlerts() {
        cleanupOldAlerts(System.currentTimeMillis(), DEFAULT_FAILED_LOGIN_WINDOW_MINUTES);
    }

    public Map<String, Object> getStatistics() {
        int trackedLogins = 0;
        for (FailedLoginWindow window : failedLogins.values()) {
            synchronized (window) {
                trackedLogins += window.timestamps.size();
            }
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalAlerts", activeAlerts.size());
        stats.put("trackedUsers", failedLogins.size());
        stats.put("trackedLogins", trackedLogins);
        return stats;
    }

    private void maybeCleanup(long eventTime, int windowMinutes) {
        long previous = lastCleanup.get();
        if ((eventTime - previous) < CLEANUP_INTERVAL_MS) {
            return;
        }
        if (lastCleanup.compareAndSet(previous, eventTime)) {
            cleanupOldAlerts(eventTime, windowMinutes);
        }
    }

    private void cleanupOldAlerts(long now, int windowMinutes) {
        long alertCutoff = now - ALERT_RETENTION_MS;
        activeAlerts.removeIf(alert -> alert.timestamp < alertCutoff);

        long staleWindowCutoff = now - Math.max(1, windowMinutes) * 60_000L;
        failedLogins.entrySet().removeIf(entry -> {
            FailedLoginWindow window = entry.getValue();
            synchronized (window) {
                while (!window.timestamps.isEmpty() && window.timestamps.peekFirst() <= staleWindowCutoff) {
                    window.timestamps.removeFirst();
                }
                return window.timestamps.isEmpty() && window.lastObserved <= staleWindowCutoff;
            }
        });

        if (failedLogins.size() > MAX_TRACKED_USERS) {
            List<Map.Entry<String, FailedLoginWindow>> candidates = new ArrayList<>(failedLogins.entrySet());
            candidates.sort((left, right) -> Long.compare(left.getValue().lastObserved, right.getValue().lastObserved));
            int removeCount = failedLogins.size() - MAX_TRACKED_USERS;
            for (int i = 0; i < removeCount && i < candidates.size(); i++) {
                failedLogins.remove(candidates.get(i).getKey(), candidates.get(i).getValue());
            }
        }
    }

    private void trimAlerts() {
        while (activeAlerts.size() > MAX_ALERTS) {
            activeAlerts.remove(0);
        }
    }
}
