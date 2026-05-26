package io.jenkins.plugins.auditlogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages startup phase detection to suppress spurious audit logs during Jenkins initialization.
 *
 * When Jenkins starts, all job configs are loaded and auto-saved, triggering SaveableListener
 * callbacks. This creates thousands of false "audit events" that aren't user-initiated.
 * This manager suppresses config-related logs during a configurable grace period.
 */
public class StartupPhaseManager {
    private static final Logger LOGGER = Logger.getLogger(StartupPhaseManager.class.getName());
    static final long DUPLICATE_SUPPRESSION_WINDOW_MS = 2_000L;

    private static volatile long startupTime = -1;
    private static volatile int gracePeriodSeconds = 30;
    private static final Map<String, Long> recentlyLoggedTargets = new ConcurrentHashMap<>();

    public static synchronized void initStartupTracking() {
        if (startupTime == -1) {
            startupTime = System.currentTimeMillis();
            LOGGER.info("Audit logging startup phase initiated. Suppressing config logs for "
                    + gracePeriodSeconds + " seconds.");

            Thread cleanup = new Thread(() -> {
                try {
                    Thread.sleep((gracePeriodSeconds + 5) * 1000L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    recentlyLoggedTargets.clear();
                    LOGGER.info("Startup grace period ended. Normal audit logging resumed.");
                }
            }, "Audit-Startup-Cleanup");
            cleanup.setDaemon(true);
            cleanup.start();
        }
    }

    public static boolean isInStartupGracePeriod() {
        if (startupTime == -1) return false;
        return (System.currentTimeMillis() - startupTime) < (gracePeriodSeconds * 1000L);
    }

    public static boolean wasRecentlyLogged(String target) {
        return wasRecentlyLogged(target, System.currentTimeMillis());
    }

    static boolean wasRecentlyLogged(String target, long nowMillis) {
        if (target == null || target.isEmpty()) return false;
        Long loggedAt = recentlyLoggedTargets.get(target);
        if (loggedAt == null) {
            return false;
        }
        if ((nowMillis - loggedAt) > DUPLICATE_SUPPRESSION_WINDOW_MS) {
            recentlyLoggedTargets.remove(target, loggedAt);
            return false;
        }
        return true;
    }

    public static void markAsLogged(String target) {
        markAsLogged(target, System.currentTimeMillis());
    }

    static void markAsLogged(String target, long nowMillis) {
        if (target != null && !target.isEmpty()) {
            if (recentlyLoggedTargets.size() >= 50000) {
                pruneExpiredTargets(nowMillis);
                if (recentlyLoggedTargets.size() >= 50000) {
                    recentlyLoggedTargets.clear();
                }
            }
            recentlyLoggedTargets.put(target, nowMillis);
        }
    }

    static void resetRecentLogsForTests() {
        recentlyLoggedTargets.clear();
    }

    private static void pruneExpiredTargets(long nowMillis) {
        recentlyLoggedTargets.entrySet().removeIf(entry -> (nowMillis - entry.getValue()) > DUPLICATE_SUPPRESSION_WINDOW_MS);
    }

    public static void setGracePeriodSeconds(int seconds) {
        if (seconds >= 5 && seconds <= 300) {
            gracePeriodSeconds = seconds;
        }
    }
}
