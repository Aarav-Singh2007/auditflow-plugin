package io.jenkins.plugins.auditlogger;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Global configuration for Jenkins Audit Logger plugin.
 * Manage Jenkins -> Configure System -> Audit Logger
 */
@Extension
public class AuditLoggerConfiguration extends GlobalConfiguration {
    private static final Logger LOGGER = Logger.getLogger(AuditLoggerConfiguration.class.getName());

    // Event Categories
    private boolean enableAuthenticationEvents = true;
    private boolean enableBuildEvents = true;
    private boolean enableJobConfigEvents = true;
    private boolean enablePipelineEvents = true;
    private boolean enableCredentialEvents = true;
    private boolean enablePluginEvents = true;
    private boolean enableSystemConfigEvents = false;
    private boolean enableNodeEvents = false;
    private boolean enableApiEvents = false;

    // Risk Detection — temporarily disabled to keep the write path lightweight.
    private boolean anomalyFailedLogins = false;
    private int anomalyFailedLoginsThreshold = 5;
    private int anomalyFailedLoginsWindowMinutes = 15;

    private boolean anomalyCredentialChanges = false;
    private int anomalyCredentialChangesThreshold = 3;

    private boolean anomalyPluginChanges = false;
    private int anomalyPluginChangesThreshold = 3;

    private boolean anomalyGlobalConfigChanges = false;
    private int anomalyGlobalConfigChangesThreshold = 5;

    private boolean anomalyJobConfigChanges = false;
    private int anomalyJobConfigChangesThreshold = 1;
    private String anomalyWatchedJobPatterns = "";

    private boolean anomalySecurityConfigChanges = false;
    private int anomalySecurityConfigChangesThreshold = 1;

    private boolean anomalyOffHoursAdmin = false;

    private boolean anomalyBuildFailures = false;
    private int anomalyBuildFailuresThreshold = 5;

    // Backward-compat aliases (kept for code that still reads old names)
    private boolean enableFailedLoginDetection = true;
    private int failedLoginThreshold = 5;
    private int failedLoginTimeWindowMinutes = 15;
    private boolean enableProductionJobChangeAlert = true;
    private boolean enableCredentialUpdateAlert = true;
    private boolean enablePluginInstallAlert = true;
    private boolean enableAdminOffHoursAlert = false;

    // Log Retention
    private int logRetentionDays = 90;
    private int maxLogFileSizeMB = 50;
    private boolean enableLogRotation = true;

    // Startup
    private int startupGracePeriodSeconds = 120;

    // Optimization
    private boolean enableAdvancedIndexing = false;
    private boolean enableAnomalyDetection = false;
    private boolean enableMetricsCollection = false;
    private int batchWriteSize = 100;
    private int batchFlushIntervalSeconds = 5;

    // Privacy
    private boolean maskTokens = true;
    private boolean maskEmailAddresses = false;
    private boolean maskCreditCards = true;

    // Alerts
    private boolean enableAlertEngine = false;
    private boolean enableComplianceReports = false;

    // UI
    private boolean enableRiskLevels = true;
    private boolean enableEventCategories = false;
    private boolean enableTimelineView = false;
    private boolean enableSensitiveEventsPanel = false;
    private boolean enableDashboardMetrics = false;
    private boolean enableDashboardStats = true;
    private boolean enableAnomalyRow = false;
    private String displayTimeZoneId = "UTC";
    private boolean showMetricTotal = true;
    private boolean showMetricLogins = true;
    private boolean showMetricFailedLogins = true;
    private boolean showMetricBuilds = true;
    private boolean showMetricJobs = true;
    private boolean showMetricConfig = true;

    // Export
    private boolean enableCsvExport = true;
    private boolean enableJsonExport = true;
    private boolean enablePdfExport = false;

    // REST API
    private boolean enableAuditApi = true;

    public AuditLoggerConfiguration() {
        load();
    }

    public static AuditLoggerConfiguration get() {
        return GlobalConfiguration.all().get(AuditLoggerConfiguration.class);
    }

    @Override
    public String getDisplayName() {
        return "AuditFlow";
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        try {
            enableAuthenticationEvents = readBoolean(json, "enableAuthenticationEvents", enableAuthenticationEvents);
            enableBuildEvents = readBoolean(json, "enableBuildEvents", enableBuildEvents);
            enableJobConfigEvents = readBoolean(json, "enableJobConfigEvents", enableJobConfigEvents);
            enablePipelineEvents = readBoolean(json, "enablePipelineEvents", enablePipelineEvents);
            enableCredentialEvents = readBoolean(json, "enableCredentialEvents", enableCredentialEvents);
            enablePluginEvents = readBoolean(json, "enablePluginEvents", enablePluginEvents);
            enableSystemConfigEvents = readBoolean(json, "enableSystemConfigEvents", enableSystemConfigEvents);
            enableNodeEvents = readBoolean(json, "enableNodeEvents", enableNodeEvents);
            enableApiEvents = readBoolean(json, "enableApiEvents", enableApiEvents);

            enableFailedLoginDetection = readBoolean(json, "enableFailedLoginDetection", enableFailedLoginDetection);
            failedLoginThreshold = readInt(json, "failedLoginThreshold", failedLoginThreshold, 1, 100);
            failedLoginTimeWindowMinutes = readInt(json, "failedLoginTimeWindowMinutes", failedLoginTimeWindowMinutes, 1, 1440);

            anomalyFailedLogins = readBoolean(json, "anomalyFailedLogins", anomalyFailedLogins);
            anomalyFailedLoginsThreshold = readInt(json, "anomalyFailedLoginsThreshold", anomalyFailedLoginsThreshold, 1, 100);
            anomalyFailedLoginsWindowMinutes = readInt(json, "anomalyFailedLoginsWindowMinutes", anomalyFailedLoginsWindowMinutes, 1, 1440);
            anomalyCredentialChanges = readBoolean(json, "anomalyCredentialChanges", anomalyCredentialChanges);
            anomalyCredentialChangesThreshold = readInt(json, "anomalyCredentialChangesThreshold", anomalyCredentialChangesThreshold, 1, 100);
            anomalyPluginChanges = readBoolean(json, "anomalyPluginChanges", anomalyPluginChanges);
            anomalyPluginChangesThreshold = readInt(json, "anomalyPluginChangesThreshold", anomalyPluginChangesThreshold, 1, 100);
            anomalyGlobalConfigChanges = readBoolean(json, "anomalyGlobalConfigChanges", anomalyGlobalConfigChanges);
            anomalyGlobalConfigChangesThreshold = readInt(json, "anomalyGlobalConfigChangesThreshold", anomalyGlobalConfigChangesThreshold, 1, 100);
            anomalyJobConfigChanges = readBoolean(json, "anomalyJobConfigChanges", anomalyJobConfigChanges);
            anomalyJobConfigChangesThreshold = readInt(json, "anomalyJobConfigChangesThreshold", anomalyJobConfigChangesThreshold, 1, 100);
            anomalyWatchedJobPatterns = readString(json, "anomalyWatchedJobPatterns", anomalyWatchedJobPatterns);
            anomalySecurityConfigChanges = readBoolean(json, "anomalySecurityConfigChanges", anomalySecurityConfigChanges);
            anomalySecurityConfigChangesThreshold = readInt(json, "anomalySecurityConfigChangesThreshold", anomalySecurityConfigChangesThreshold, 1, 100);
            anomalyOffHoursAdmin = readBoolean(json, "anomalyOffHoursAdmin", anomalyOffHoursAdmin);
            anomalyBuildFailures = readBoolean(json, "anomalyBuildFailures", anomalyBuildFailures);
            anomalyBuildFailuresThreshold = readInt(json, "anomalyBuildFailuresThreshold", anomalyBuildFailuresThreshold, 1, 100);

            enableProductionJobChangeAlert = readBoolean(json, "enableProductionJobChangeAlert", enableProductionJobChangeAlert);
            enableCredentialUpdateAlert = readBoolean(json, "enableCredentialUpdateAlert", enableCredentialUpdateAlert);
            enablePluginInstallAlert = readBoolean(json, "enablePluginInstallAlert", enablePluginInstallAlert);
            enableAdminOffHoursAlert = readBoolean(json, "enableAdminOffHoursAlert", enableAdminOffHoursAlert);

            logRetentionDays = readInt(json, "logRetentionDays", logRetentionDays, 0, 3650);
            maxLogFileSizeMB = readInt(json, "maxLogFileSizeMB", maxLogFileSizeMB, 1, 1024);
            enableLogRotation = readBoolean(json, "enableLogRotation", enableLogRotation);

            startupGracePeriodSeconds = readInt(json, "startupGracePeriodSeconds", startupGracePeriodSeconds, 5, 300);
            StartupPhaseManager.setGracePeriodSeconds(startupGracePeriodSeconds);

            enableAdvancedIndexing = readBoolean(json, "enableAdvancedIndexing", enableAdvancedIndexing);
            enableAnomalyDetection = false;
            enableMetricsCollection = readBoolean(json, "enableMetricsCollection", enableMetricsCollection);
            batchWriteSize = readInt(json, "batchWriteSize", batchWriteSize, 1, 10000);
            batchFlushIntervalSeconds = readInt(json, "batchFlushIntervalSeconds", batchFlushIntervalSeconds, 1, 300);

            maskTokens = readBoolean(json, "maskTokens", maskTokens);
            maskEmailAddresses = readBoolean(json, "maskEmailAddresses", maskEmailAddresses);
            maskCreditCards = readBoolean(json, "maskCreditCards", maskCreditCards);

            enableAlertEngine = readBoolean(json, "enableAlertEngine", enableAlertEngine);
            enableComplianceReports = readBoolean(json, "enableComplianceReports", enableComplianceReports);

            enableRiskLevels = readBoolean(json, "enableRiskLevels", enableRiskLevels);
            enableEventCategories = readBoolean(json, "enableEventCategories", enableEventCategories);
            enableTimelineView = readBoolean(json, "enableTimelineView", enableTimelineView);
            enableSensitiveEventsPanel = readBoolean(json, "enableSensitiveEventsPanel", enableSensitiveEventsPanel);
            enableDashboardMetrics = readBoolean(json, "enableDashboardMetrics", enableDashboardMetrics);
            enableDashboardStats = readBoolean(json, "enableDashboardStats", enableDashboardStats);
            enableAnomalyRow = false;
            displayTimeZoneId = sanitizeTimeZoneId(readString(json, "displayTimeZoneId", displayTimeZoneId));
            showMetricTotal = readBoolean(json, "showMetricTotal", showMetricTotal);
            showMetricLogins = readBoolean(json, "showMetricLogins", showMetricLogins);
            showMetricFailedLogins = readBoolean(json, "showMetricFailedLogins", showMetricFailedLogins);
            showMetricBuilds = readBoolean(json, "showMetricBuilds", showMetricBuilds);
            showMetricJobs = readBoolean(json, "showMetricJobs", showMetricJobs);
            showMetricConfig = readBoolean(json, "showMetricConfig", showMetricConfig);

            enableCsvExport = readBoolean(json, "enableCsvExport", enableCsvExport);
            enableJsonExport = readBoolean(json, "enableJsonExport", enableJsonExport);
            enablePdfExport = readBoolean(json, "enablePdfExport", enablePdfExport);

            enableAuditApi = readBoolean(json, "enableAuditApi", enableAuditApi);

            save();
            LOGGER.info("Audit Logger configuration updated");
            return true;
        } catch (Exception e) {
            LOGGER.warning("Error saving Audit Logger configuration: " + e.getMessage());
            return false;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean readBoolean(JSONObject json, String key, boolean currentValue) {
        return json.has(key) ? json.optBoolean(key, currentValue) : currentValue;
    }

    private static int readInt(JSONObject json, String key, int currentValue, int min, int max) {
        return json.has(key) ? clamp(json.optInt(key, currentValue), min, max) : currentValue;
    }

    private static String readString(JSONObject json, String key, String currentValue) {
        return json.has(key) ? json.optString(key, currentValue) : currentValue;
    }

    private static String sanitizeTimeZoneId(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "UTC";
        }

        String candidate = value.trim();
        if ("SYSTEM".equalsIgnoreCase(candidate)) {
            return "SYSTEM";
        }

        try {
            return ZoneId.of(candidate).getId();
        } catch (DateTimeException ignored) {
            return "UTC";
        }
    }

    // --- Getters ---

    public boolean isEnableAuthenticationEvents() { return enableAuthenticationEvents; }
    public boolean isEnableBuildEvents() { return enableBuildEvents; }
    public boolean isEnableJobConfigEvents() { return enableJobConfigEvents; }
    public boolean isEnablePipelineEvents() { return enablePipelineEvents; }
    public boolean isEnableCredentialEvents() { return enableCredentialEvents; }
    public boolean isEnablePluginEvents() { return enablePluginEvents; }
    public boolean isEnableSystemConfigEvents() { return enableSystemConfigEvents; }
    public boolean isEnableNodeEvents() { return enableNodeEvents; }
    public boolean isEnableApiEvents() { return enableApiEvents; }

    public boolean isEnableFailedLoginDetection() { return enableFailedLoginDetection; }
    public int getFailedLoginThreshold() { return failedLoginThreshold; }
    public int getFailedLoginTimeWindowMinutes() { return failedLoginTimeWindowMinutes; }
    public boolean isEnableProductionJobChangeAlert() { return enableProductionJobChangeAlert; }
    public boolean isEnableCredentialUpdateAlert() { return enableCredentialUpdateAlert; }
    public boolean isEnablePluginInstallAlert() { return enablePluginInstallAlert; }

    // Anomaly Detection getters
    public boolean isAnomalyFailedLogins() { return anomalyFailedLogins; }
    public int getAnomalyFailedLoginsThreshold() { return anomalyFailedLoginsThreshold; }
    public int getAnomalyFailedLoginsWindowMinutes() { return anomalyFailedLoginsWindowMinutes; }
    public boolean isAnomalyCredentialChanges() { return anomalyCredentialChanges; }
    public int getAnomalyCredentialChangesThreshold() { return anomalyCredentialChangesThreshold; }
    public boolean isAnomalyPluginChanges() { return anomalyPluginChanges; }
    public int getAnomalyPluginChangesThreshold() { return anomalyPluginChangesThreshold; }
    public boolean isAnomalyGlobalConfigChanges() { return anomalyGlobalConfigChanges; }
    public int getAnomalyGlobalConfigChangesThreshold() { return anomalyGlobalConfigChangesThreshold; }
    public boolean isAnomalyJobConfigChanges() { return anomalyJobConfigChanges; }
    public int getAnomalyJobConfigChangesThreshold() { return anomalyJobConfigChangesThreshold; }
    public String getAnomalyWatchedJobPatterns() { return anomalyWatchedJobPatterns != null ? anomalyWatchedJobPatterns : ""; }
    public boolean isAnomalySecurityConfigChanges() { return anomalySecurityConfigChanges; }
    public int getAnomalySecurityConfigChangesThreshold() { return anomalySecurityConfigChangesThreshold; }
    public boolean isAnomalyOffHoursAdmin() { return anomalyOffHoursAdmin; }
    public boolean isAnomalyBuildFailures() { return anomalyBuildFailures; }
    public int getAnomalyBuildFailuresThreshold() { return anomalyBuildFailuresThreshold; }
    public boolean isEnableAdminOffHoursAlert() { return enableAdminOffHoursAlert; }

    public int getLogRetentionDays() { return logRetentionDays; }
    public int getMaxLogFileSizeMB() { return maxLogFileSizeMB; }
    public long getMaxLogFileSizeBytes() { return (long) maxLogFileSizeMB * 1024L * 1024L; }
    public boolean isEnableLogRotation() { return enableLogRotation; }

    public int getStartupGracePeriodSeconds() { return startupGracePeriodSeconds; }

    public boolean isEnableAdvancedIndexing() { return enableAdvancedIndexing; }
    public boolean isEnableAnomalyDetection() { return false; }
    public boolean isEnableMetricsCollection() { return enableMetricsCollection; }
    public int getBatchWriteSize() { return batchWriteSize; }
    public int getBatchFlushIntervalSeconds() { return batchFlushIntervalSeconds; }

    public boolean isMaskTokens() { return maskTokens; }
    public boolean isMaskEmailAddresses() { return maskEmailAddresses; }
    public boolean isMaskCreditCards() { return maskCreditCards; }

    public boolean isEnableAlertEngine() { return enableAlertEngine; }
    public boolean isEnableComplianceReports() { return enableComplianceReports; }

    public boolean isEnableRiskLevels() { return enableRiskLevels; }
    public boolean isEnableEventCategories() { return enableEventCategories; }
    public boolean isEnableTimelineView() { return enableTimelineView; }
    public boolean isEnableSensitiveEventsPanel() { return enableSensitiveEventsPanel; }
    public boolean isEnableDashboardMetrics() { return enableDashboardMetrics; }
    public boolean isEnableDashboardStats() { return enableDashboardStats; }
    public boolean isEnableAnomalyRow() { return false; }
    public String getDisplayTimeZoneId() { return displayTimeZoneId; }
    public ZoneId getDisplayTimeZone() {
        return "SYSTEM".equalsIgnoreCase(displayTimeZoneId)
                ? ZoneId.systemDefault()
                : ZoneId.of(displayTimeZoneId);
    }
    public List<String> getAvailableDisplayTimeZoneIds() {
        // Curated list of professional timezones commonly used in enterprise environments
        List<String> professionalTimeZones = Arrays.asList(
            "SYSTEM",
            "UTC",
            "America/New_York",
            "America/Chicago",
            "America/Los_Angeles",
            "Europe/London",
            "Europe/Berlin",
            "Europe/Paris",
            "Asia/Tokyo",
            "Asia/Shanghai",
            "Asia/Singapore",
            "Australia/Sydney"
        );
        return new ArrayList<>(professionalTimeZones);
    }
    public boolean isShowMetricTotal() { return showMetricTotal; }
    public boolean isShowMetricLogins() { return showMetricLogins; }
    public boolean isShowMetricFailedLogins() { return showMetricFailedLogins; }
    public boolean isShowMetricBuilds() { return showMetricBuilds; }
    public boolean isShowMetricJobs() { return showMetricJobs; }
    public boolean isShowMetricConfig() { return showMetricConfig; }

    public boolean isEnableCsvExport() { return enableCsvExport; }
    public boolean isEnableJsonExport() { return enableJsonExport; }
    public boolean isEnablePdfExport() { return enablePdfExport; }

    public boolean isEnableAuditApi() { return enableAuditApi; }

    /** Return anomaly detection rules as a JSON string for dashboard JS consumption. */
    public String getAnomalyConfigJson() {
        Map<String, Object> anomalyConfig = new LinkedHashMap<>();
        anomalyConfig.put("failedLoginsThreshold", anomalyFailedLoginsThreshold);
        anomalyConfig.put("credentialChangesThreshold", anomalyCredentialChangesThreshold);
        anomalyConfig.put("pluginChangesThreshold", anomalyPluginChangesThreshold);
        anomalyConfig.put("globalConfigChangesThreshold", anomalyGlobalConfigChangesThreshold);
        anomalyConfig.put("jobConfigChangesThreshold", anomalyJobConfigChangesThreshold);
        anomalyConfig.put("securityConfigChangesThreshold", anomalySecurityConfigChangesThreshold);
        anomalyConfig.put("buildFailuresThreshold", anomalyBuildFailuresThreshold);
        return new com.google.gson.Gson().toJson(anomalyConfig);
    }

    private static String escapeJsonString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
