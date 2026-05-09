package io.jenkins.plugins.auditlogger;

import hudson.Extension;
import hudson.model.PeriodicWork;
import jenkins.model.Jenkins;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Periodic log retention cleanup. Runs hourly.
 * Deletes rotated log files older than the configured retention period.
 * Does NOT delete the current active log file.
 */
@Extension
public class LogRotationService extends PeriodicWork {
    private static final Logger LOGGER = Logger.getLogger(LogRotationService.class.getName());
    private static final String ACTIVE_LOG = "audit.jsonl";

    @Override
    public long getRecurrencePeriod() {
        return 3_600_000; // 1 hour
    }

    @Override
    protected void doRun() {
        try {
            AuditLoggerConfiguration config = AuditLoggerConfiguration.get();
            if (config == null) return;

            int retentionDays = config.getLogRetentionDays();
            if (retentionDays <= 0) return; // 0 = keep forever

            deleteOldLogs(retentionDays);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in log rotation service", e);
        }
    }

    private void deleteOldLogs(int retentionDays) {
        File logsDir = getAuditLogsDir();
        if (!logsDir.exists()) return;

        long retentionMs = (long) retentionDays * 24 * 60 * 60 * 1000;
        long cutoff = System.currentTimeMillis() - retentionMs;

        File[] files = logsDir.listFiles((dir, name) ->
                name.startsWith("audit") && (name.endsWith(".jsonl") || name.endsWith(".log"))
                        && !ACTIVE_LOG.equals(name)); // never delete active log

        if (files == null) return;

        for (File file : files) {
            if (file.lastModified() < cutoff) {
                if (file.delete()) {
                    LOGGER.info("Deleted old audit log: " + file.getName());
                } else {
                    LOGGER.warning("Failed to delete old audit log: " + file.getName());
                }
            }
        }
    }

    private File getAuditLogsDir() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) return new File("auditflow-logs");
        return new File(jenkins.getRootDir(), "auditflow-logs");
    }
}
