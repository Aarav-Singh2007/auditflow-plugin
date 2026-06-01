package io.jenkins.plugins.auditlogger;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class AuditLoggerManagementLinkInsightsRegressionTest {

    @Test
    @SuppressWarnings("unchecked")
    void buildAnomalyConfigProvidesStableInsightThresholdDefaults() throws Exception {
        Method buildAnomalyConfig = AuditLoggerManagementLink.class
                .getDeclaredMethod("buildAnomalyConfig", AuditLoggerConfiguration.class);
        buildAnomalyConfig.setAccessible(true);

        Map<String, Object> config = (Map<String, Object>) buildAnomalyConfig.invoke(null, new Object[] { null });

        assertEquals(5, config.get("failedLoginsThreshold"));
        assertEquals(3, config.get("credentialChangesThreshold"));
        assertEquals(3, config.get("pluginChangesThreshold"));
        assertEquals(5, config.get("globalConfigChangesThreshold"));
        assertEquals(1, config.get("jobConfigChangesThreshold"));
        assertEquals(1, config.get("securityConfigChangesThreshold"));
        assertEquals(5, config.get("buildFailuresThreshold"));
    }
    @Test
    void buildInsightsCountsBulkPluginUpdatesCorrectly() throws Exception {
        long now = System.currentTimeMillis();
        AuditLogEntry entry1 = new AuditLogEntry("admin", "PLUGIN_UPDATED", "pluginA", "", now);
        AuditLogEntry entry2 = new AuditLogEntry("admin", "PLUGIN_UPDATED", "pluginB,pluginC,pluginD", "", now);
        AuditLogEntry entry3 = new AuditLogEntry("admin", "PLUGIN_INSTALLED", "pluginE,pluginF", "", now);

        AuditLogEntry entry4 = new AuditLogEntry("admin", "PLUGIN_REMOVED", "dummy", "", now);
        java.lang.reflect.Field targetField = AuditLogEntry.class.getDeclaredField("target");
        targetField.setAccessible(true);
        targetField.set(entry4, null);

        AuditLogEntry entry5 = new AuditLogEntry("admin", "PLUGIN_REMOVED", "dummy", "", now);
        targetField.set(entry5, "");

        java.util.List<AuditLogEntry> entries = java.util.Arrays.asList(entry1, entry2, entry3, entry4, entry5);

        java.util.List<Map<String, Object>> insights = AuditLoggerManagementLink.buildInsights(
                entries, null, java.time.ZoneOffset.UTC);

        Map<String, Object> pluginInsight = insights.stream()
                .filter(i -> "Plugin changes".equals(i.get("text")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Plugin changes insight not found"));

        // count should be: 1 (entry1) + 3 (entry2) + 2 (entry3) + 1 (entry4 null fallback) + 1 (entry5 empty fallback) = 8
        assertEquals(8, pluginInsight.get("count"));
    }
}