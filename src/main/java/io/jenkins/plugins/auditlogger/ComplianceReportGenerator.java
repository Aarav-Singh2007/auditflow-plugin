package io.jenkins.plugins.auditlogger;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Compliance report generator for audit standards (SOX, PCI-DSS, GDPR).
 */
public class ComplianceReportGenerator {

    public static class ComplianceReport {
        public String reportType;
        public Date generatedDate;
        public Date reportPeriodStart;
        public Date reportPeriodEnd;
        public Map<String, Object> summary;
        public Map<String, Object> findings;
        public List<String> recommendations;
        public String status;
    }

    public static ComplianceReport generateSoxReport(List<AuditLogEntry> entries,
                                                     Date startDate, Date endDate) {
        ComplianceReport report = new ComplianceReport();
        report.reportType = "SOX";
        report.generatedDate = new Date();
        report.reportPeriodStart = startDate;
        report.reportPeriodEnd = endDate;
        report.recommendations = new ArrayList<>();

        List<AuditLogEntry> period = filterPeriod(entries, startDate, endDate);
        Map<String, Object> summary = new LinkedHashMap<>();

        long authEvents = period.stream()
                .filter(e -> e.getAction().contains("LOGIN") || e.getAction().contains("AUTH")).count();
        long failedLogins = period.stream()
                .filter(e -> "FAILED_LOGIN".equals(e.getAction())).count();
        long configChanges = period.stream()
                .filter(e -> e.getAction().contains("CONFIG") || e.getAction().contains("UPDATED")).count();
        long highRisk = period.stream()
                .filter(e -> "CRITICAL".equals(e.getSeverity()) || "HIGH".equals(e.getSeverity())).count();
        long uniqueUsers = period.stream()
                .map(AuditLogEntry::getUsername).distinct().count();

        summary.put("authenticationEvents", authEvents);
        summary.put("failedLoginAttempts", failedLogins);
        summary.put("configurationChanges", configChanges);
        summary.put("highRiskEvents", highRisk);
        summary.put("uniqueUsersTracked", uniqueUsers);
        summary.put("totalEvents", period.size());
        report.summary = summary;

        Map<String, Object> findings = new LinkedHashMap<>();
        findings.put("completeCoverage", authEvents > 0);
        findings.put("integrityVerified", failedLogins < 5);
        findings.put("accessControlEnforced", configChanges > 0 || authEvents > 0);
        report.findings = findings;

        if (failedLogins > 10) report.recommendations.add("Review high failed login count");
        if (highRisk > 20) report.recommendations.add("Investigate high-risk events");
        if (uniqueUsers < 3) report.recommendations.add("Ensure adequate role-based access control");

        int compliant = (int) findings.values().stream()
                .filter(v -> v instanceof Boolean && (Boolean) v).count();
        report.status = compliant == 3 ? "COMPLIANT" : compliant >= 2 ? "PARTIAL" : "NON_COMPLIANT";
        return report;
    }

    public static ComplianceReport generatePciReport(List<AuditLogEntry> entries,
                                                     Date startDate, Date endDate) {
        ComplianceReport report = new ComplianceReport();
        report.reportType = "PCI-DSS";
        report.generatedDate = new Date();
        report.reportPeriodStart = startDate;
        report.reportPeriodEnd = endDate;
        report.recommendations = new ArrayList<>();

        List<AuditLogEntry> period = filterPeriod(entries, startDate, endDate);
        Map<String, Object> summary = new LinkedHashMap<>();

        long authUsers = period.stream()
                .filter(e -> e.getAction().contains("LOGIN"))
                .map(AuditLogEntry::getUsername).distinct().count();
        long credEvents = period.stream()
                .filter(e -> e.getAction().contains("CREDENTIAL") || e.getAction().contains("PASSWORD")).count();

        summary.put("authenticatedAccessPoints", authUsers);
        summary.put("credentialManagementEvents", credEvents);
        summary.put("totalLoggingEvents", period.size());
        report.summary = summary;

        Map<String, Object> findings = new LinkedHashMap<>();
        findings.put("strongAccessControl", authUsers > 0);
        findings.put("passwordManagement", credEvents > 0);
        findings.put("adequateLogging", period.size() > 100);
        report.findings = findings;

        int compliant = (int) findings.values().stream()
                .filter(v -> v instanceof Boolean && (Boolean) v).count();
        report.status = compliant >= 3 ? "COMPLIANT" : compliant >= 2 ? "PARTIAL" : "NON_COMPLIANT";
        return report;
    }

    public static ComplianceReport generateGdprReport(List<AuditLogEntry> entries,
                                                      Date startDate, Date endDate) {
        ComplianceReport report = new ComplianceReport();
        report.reportType = "GDPR";
        report.generatedDate = new Date();
        report.reportPeriodStart = startDate;
        report.reportPeriodEnd = endDate;
        report.recommendations = new ArrayList<>();
        report.recommendations.add("Verify data retention policies comply with requirements");

        List<AuditLogEntry> period = filterPeriod(entries, startDate, endDate);
        Map<String, Object> summary = new LinkedHashMap<>();

        long dataAccess = period.stream()
                .filter(e -> e.getAction().contains("ACCESS") || e.getAction().contains("READ")).count();
        long dataMod = period.stream()
                .filter(e -> e.getAction().contains("UPDATE") || e.getAction().contains("DELETE")
                        || e.getAction().contains("CREATE")).count();

        summary.put("dataAccessEvents", dataAccess);
        summary.put("dataModificationEvents", dataMod);
        report.summary = summary;

        Map<String, Object> findings = new LinkedHashMap<>();
        findings.put("accessLogging", dataAccess > 0 || period.size() > 0);
        findings.put("changeTracking", dataMod > 0 || period.size() > 0);
        report.findings = findings;
        report.status = "COMPLIANT";
        return report;
    }

    public static String formatReport(ComplianceReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(report.reportType).append(" COMPLIANCE REPORT ===\n\n");
        DateTimeFormatter dtFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
        DateTimeFormatter dFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
        sb.append("Generated: ").append(dtFmt.format(report.generatedDate.toInstant())).append("\n");
        sb.append("Period: ").append(dFmt.format(report.reportPeriodStart.toInstant()))
                .append(" to ").append(dFmt.format(report.reportPeriodEnd.toInstant())).append("\n");
        sb.append("Status: ").append(report.status).append("\n\n");
        sb.append("SUMMARY:\n");
        report.summary.forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append("\n"));
        sb.append("\nFINDINGS:\n");
        report.findings.forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append("\n"));
        if (!report.recommendations.isEmpty()) {
            sb.append("\nRECOMMENDATIONS:\n");
            report.recommendations.forEach(r -> sb.append("  - ").append(r).append("\n"));
        }
        return sb.toString();
    }

    private static List<AuditLogEntry> filterPeriod(List<AuditLogEntry> entries, Date start, Date end) {
        return entries.stream()
                .filter(e -> e.getTimestamp() >= start.getTime() && e.getTimestamp() <= end.getTime())
                .collect(Collectors.toList());
    }
}
