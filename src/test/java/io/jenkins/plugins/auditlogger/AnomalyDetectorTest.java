package io.jenkins.plugins.auditlogger;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class AnomalyDetectorTest {

    /** Captures emails instead of calling Transport.send */
    private final List<jakarta.mail.internet.MimeMessage> sentEmails = new ArrayList<>();

    /** Tracks whether sendEmail threw */
    private boolean sendEmailThrew = false;

    private AnomalyDetector detector;

    @BeforeEach
    void setup() {
        sentEmails.clear();
        sendEmailThrew = false;
        detector = new AnomalyDetector() {
            @Override
            protected void sendEmail(jakarta.mail.internet.MimeMessage msg) throws Exception {
                sentEmails.add(msg); 
            }
        };
    }

   

    @Test
    void testBruteForceLoginAnomalyIsDetected(JenkinsRule j) {
        AuditLoggerConfiguration config = new AuditLoggerConfiguration();
        config.setAnomalyFailedLogins(true);
        config.setAnomalyFailedLoginsThreshold(3);
        config.setAnomalyFailedLoginsWindowMinutes(1);
        config.setEnableEmailAlerts(false);

        long now = System.currentTimeMillis();
        detector.analyze(new AuditLogEntry("testuser", "FAILED_LOGIN", "jenkins", "", now), config);
        assertEquals(0, detector.getAlerts(10).size(), "Should not trigger yet");

        detector.analyze(new AuditLogEntry("testuser", "FAILED_LOGIN", "jenkins", "", now + 1000), config);
        assertEquals(0, detector.getAlerts(10).size(), "Should not trigger yet");

        detector.analyze(new AuditLogEntry("testuser", "FAILED_LOGIN", "jenkins", "", now + 2000), config);

        List<AnomalyDetector.AnomalyAlert> alerts = detector.getAlerts(10);
        assertEquals(1, alerts.size(), "Should trigger alert");
        assertEquals(AnomalyDetector.AnomalyType.BRUTE_FORCE_LOGIN, alerts.get(0).type);
        assertEquals("testuser", alerts.get(0).user);
    }

    

    @Test
    void testEmailSentWithValidAdminAddress(JenkinsRule j) throws Exception {
        AuditLoggerConfiguration config = new AuditLoggerConfiguration();
        config.setAnomalyFailedLogins(true);
        config.setAnomalyFailedLoginsThreshold(2);
        config.setAnomalyFailedLoginsWindowMinutes(1);
        config.setEnableEmailAlerts(true);
        config.setAlertEmailAddresses("test@example.com");

        // Set a valid admin address so the true-branch of L228 is hit
        hudson.tasks.Mailer.descriptor().setAdminAddress("admin@jenkins.local");

        long now = System.currentTimeMillis();
        detector.analyze(new AuditLogEntry("u1", "FAILED_LOGIN", "jenkins", "", now), config);
        detector.analyze(new AuditLogEntry("u1", "FAILED_LOGIN", "jenkins", "", now + 1000), config);

        assertEquals(1, detector.getAlerts(10).size());
        assertEquals(1, sentEmails.size(), "Email should have been sent");
    }

    // ── Line 228+231: null/empty admin address → fallback "auditflow@localhost" ─

    @Test
    void testEmailSentWithFallbackFromAddress(JenkinsRule j) throws Exception {
        AuditLoggerConfiguration config = new AuditLoggerConfiguration();
        config.setAnomalyFailedLogins(true);
        config.setAnomalyFailedLoginsThreshold(2);
        config.setAnomalyFailedLoginsWindowMinutes(1);
        config.setEnableEmailAlerts(true);
        config.setAlertEmailAddresses("test@example.com, ");

     
        hudson.tasks.Mailer.descriptor().setAdminAddress(null);

        long now = System.currentTimeMillis();
        detector.analyze(new AuditLogEntry("emailuser", "FAILED_LOGIN", "jenkins", "", now), config);
        detector.analyze(new AuditLogEntry("emailuser", "FAILED_LOGIN", "jenkins", "", now + 1000), config);

        assertEquals(1, detector.getAlerts(10).size());
        assertEquals(1, sentEmails.size(), "Email should have been sent with fallback from-address");
    }

   

    @Test
    void testNullEmailAddressesSkipsSend(JenkinsRule j) {
        AuditLoggerConfiguration config = new AuditLoggerConfiguration();
        config.setAnomalyFailedLogins(true);
        config.setAnomalyFailedLoginsThreshold(1);
        config.setAnomalyFailedLoginsWindowMinutes(1);
        config.setEnableEmailAlerts(true);
        config.setAlertEmailAddresses(null);

        detector.analyze(new AuditLogEntry("nullmail", "FAILED_LOGIN", "jenkins", "", System.currentTimeMillis()), config);

        assertEquals(1, detector.getAlerts(10).size());
        assertEquals(0, sentEmails.size(), "No email when addresses are null");
    }

    

    @Test
    void testEmptyEmailAddressesSkipsSend(JenkinsRule j) {
        AuditLoggerConfiguration config = new AuditLoggerConfiguration();
        config.setAnomalyFailedLogins(true);
        config.setAnomalyFailedLoginsThreshold(1);
        config.setAnomalyFailedLoginsWindowMinutes(1);
        config.setEnableEmailAlerts(true);
        config.setAlertEmailAddresses("   ");

        detector.analyze(new AuditLogEntry("emptymail", "FAILED_LOGIN", "jenkins", "", System.currentTimeMillis()), config);

        assertEquals(1, detector.getAlerts(10).size());
        assertEquals(0, sentEmails.size(), "No email when addresses are blank");
    }

    

    @Test
    void testExceptionDuringEmailSendIsCaught(JenkinsRule j) {
        
        detector = new AnomalyDetector() {
            @Override
            protected void sendEmail(jakarta.mail.internet.MimeMessage msg) throws Exception {
                throw new jakarta.mail.MessagingException("Simulated SMTP failure");
            }
        };

        AuditLoggerConfiguration config = new AuditLoggerConfiguration();
        config.setAnomalyFailedLogins(true);
        config.setAnomalyFailedLoginsThreshold(2);
        config.setAnomalyFailedLoginsWindowMinutes(1);
        config.setEnableEmailAlerts(true);
        config.setAlertEmailAddresses("fail@example.com");

        hudson.tasks.Mailer.descriptor().setAdminAddress("admin@jenkins.local");

        long now = System.currentTimeMillis();
        // Should NOT throw — exception is caught inside sendEmailNotification
        detector.analyze(new AuditLogEntry("failuser", "FAILED_LOGIN", "jenkins", "", now), config);
        detector.analyze(new AuditLogEntry("failuser", "FAILED_LOGIN", "jenkins", "", now + 1000), config);

        assertEquals(1, detector.getAlerts(10).size(), "Alert should still be recorded despite email failure");
    }

    

    @Test
    void testRealSendEmailMethodCoversTransportLine(JenkinsRule j) {
        // Use a real AnomalyDetector (not mocked) — Transport.send will throw
        // because there's no SMTP server, which also covers the catch block (L241-242).
        AnomalyDetector realDetector = new AnomalyDetector();

        AuditLoggerConfiguration config = new AuditLoggerConfiguration();
        config.setAnomalyFailedLogins(true);
        config.setAnomalyFailedLoginsThreshold(2);
        config.setAnomalyFailedLoginsWindowMinutes(1);
        config.setEnableEmailAlerts(true);
        config.setAlertEmailAddresses("real@example.com");

        hudson.tasks.Mailer.descriptor().setAdminAddress("admin@jenkins.local");

        long now = System.currentTimeMillis();
       
        realDetector.analyze(new AuditLogEntry("realuser", "FAILED_LOGIN", "jenkins", "", now), config);
        realDetector.analyze(new AuditLogEntry("realuser", "FAILED_LOGIN", "jenkins", "", now + 1000), config);

        assertEquals(1, realDetector.getAlerts(10).size(), "Alert recorded even when Transport.send fails");
    }

    

    @Test
    void testNullConfigDoesNotCrash(JenkinsRule j) {
        detector.analyze(new AuditLogEntry("x", "FAILED_LOGIN", "jenkins", "", System.currentTimeMillis()), null);
        assertTrue(detector.getAlerts(10).isEmpty());
    }

    

    @Test
    void testResetForTestCoversStaticReset(JenkinsRule j) {
        AuditLogStorage.resetForTest();
       
        AuditLogStorage storage = AuditLogStorage.getInstance();
        assertTrue(storage != null, "getInstance should return a non-null instance after reset");
    }
}
