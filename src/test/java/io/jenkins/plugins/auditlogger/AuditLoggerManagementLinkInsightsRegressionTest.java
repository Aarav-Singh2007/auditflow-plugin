package io.jenkins.plugins.auditlogger;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class AuditLoggerManagementLinkInsightsRegressionTest {

    @Test
    @SuppressWarnings("unchecked")
    public void buildAnomalyConfigProvidesStableInsightThresholdDefaults() throws Exception {
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
}