package io.jenkins.plugins.auditlogger;

import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class CoverageFixesTest {

    @Test
    void testAnomalyDetectorBruteForce() {
        AnomalyDetector detector = new AnomalyDetector();
        
        // Trigger 4 failures - no alert
        for (int i = 0; i < 4; i++) {
            detector.analyze(new AuditLogEntry("test_user", "FAILED_LOGIN", "target", "details", System.currentTimeMillis()));
        }
        assertTrue(detector.getAlerts(10).isEmpty());
        
        // 5th failure triggers alert
        detector.analyze(new AuditLogEntry("test_user", "FAILED_LOGIN", "target", "details", System.currentTimeMillis()));
        assertTrue(!detector.getAlerts(10).isEmpty());
        assertTrue(detector.getAlerts(10).get(0).type == AnomalyDetector.AnomalyType.BRUTE_FORCE_LOGIN);
    }

    @Test
    void testAnomalyDetectorOldLoginsIgnored() {
        AnomalyDetector detector = new AnomalyDetector();
        
        long now = System.currentTimeMillis();
        long twoMinutesAgo = now - 120_000;
        
        // Trigger 4 failures that are old
        for (int i = 0; i < 4; i++) {
            detector.analyze(new AuditLogEntry("old_user", "FAILED_LOGIN", "target", "details", twoMinutesAgo));
        }
        
        // Trigger 1 failure now
        detector.analyze(new AuditLogEntry("old_user", "FAILED_LOGIN", "target", "details", now));
        
        // Should be empty because the 4 old failures were ignored, so recent count is 1
        assertTrue(detector.getAlerts(10).isEmpty());
    }

    @Test
    void testAnomalyDetectorReturnsLatestAlertsWhenLimited() {
        AnomalyDetector detector = new AnomalyDetector();

        for (int i = 0; i < 5; i++) {
            detector.analyze(new AuditLogEntry("user-one", "FAILED_LOGIN", "target", "details", 1_000L + i));
        }
        for (int i = 0; i < 5; i++) {
            detector.analyze(new AuditLogEntry("user-two", "FAILED_LOGIN", "target", "details", 2_000L + i));
        }

        assertTrue(detector.getAlerts(1).size() == 1);
        assertTrue("user-two".equals(detector.getAlerts(1).get(0).user));
    }



    @Test
    void testAuditLogStorageExceptionCaught() throws Exception {
        // Inject an anonymous anomaly detector that throws an exception
        AnomalyDetector mockDetector = new AnomalyDetector() {
            @Override
            public void analyze(AuditLogEntry entry, AuditLoggerConfiguration config) {
                throw new RuntimeException("Simulated exception");
            }
        };
        
        AuditLogStorage storage = AuditLogStorage.getInstance();
        Field detectorField = AuditLogStorage.class.getDeclaredField("anomalyDetector");
        detectorField.setAccessible(true);
        AnomalyDetector original = (AnomalyDetector) detectorField.get(storage);
        
        try {
            detectorField.set(storage, mockDetector);
            
            // This should not throw; the exception should be caught in addEntry
            storage.addEntry(new AuditLogEntry("user", "action", "target", "details", 0L));
            
        } finally {
            // Restore original detector
            detectorField.set(storage, original);
        }
    }
}
