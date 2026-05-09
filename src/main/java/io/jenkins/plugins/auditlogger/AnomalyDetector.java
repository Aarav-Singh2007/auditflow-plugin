package io.jenkins.plugins.auditlogger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anomaly detection is temporarily disabled to keep the audit hot path lean.
 * This placeholder preserves the existing API surface for the dashboard code.
 */
public class AnomalyDetector {

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
            this.type = type;
            this.user = user;
            this.details = details;
            this.timestamp = System.currentTimeMillis();
            this.severity = severity;
        }
    }

    public void analyze(AuditLogEntry entry) {
        // Intentionally disabled.
    }

    public List<AnomalyAlert> getAlerts(int limit) {
        return Collections.emptyList();
    }

    public void cleanupOldAlerts() {
        // Intentionally disabled.
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalAlerts", 0);
        stats.put("trackedUsers", 0);
        stats.put("trackedLogins", 0);
        return stats;
    }
}
