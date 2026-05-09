package io.jenkins.plugins.auditlogger;

import org.junit.Test;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class AuditLoggerManagementLinkPaginationRegressionTest {

    @Test
    public void filterAndPaginateKeepsNewestEntriesOnFirstServerPage() {
        List<AuditLogEntry> entries = Arrays.asList(
                entry("alice", "LOGIN", 1_700L),
                entry("SYSTEM", "PLUGIN_ENABLED", 2_000L),
                entry("bob", "BUILD_STARTED", 3_000L),
                entry("charlie", "JOB_CREATED", 4_000L));

        AuditLoggerManagementLink.AuditViewRequest request = new AuditLoggerManagementLink.AuditViewRequest(
                null,
                "all",
                null,
                null,
                null,
                "user-only",
                "timestampMs",
                false,
                1,
                2);

        List<AuditLogEntry> filtered = AuditLoggerManagementLink.filterAndSortEntries(entries, request, ZoneId.of("UTC"));
        AuditLoggerManagementLink.PageSlice page = AuditLoggerManagementLink.paginateEntries(filtered, request.page, request.pageSize);

        assertEquals(3, filtered.size());
        assertEquals(2, page.entries.size());
        assertEquals("charlie", page.entries.get(0).getUsername());
        assertEquals("bob", page.entries.get(1).getUsername());
    }

    @Test
    public void toDisplayEntryUsesConfiguredDisplayTimezone() {
        AuditLogEntry entry = entry("alice", "LOGIN", 1_746_691_200_000L);
        ZoneId zone = ZoneId.of("Asia/Kolkata");

        Map<String, Object> display = AuditLoggerManagementLink.toDisplayEntry(entry, zone);

        assertEquals(entry.getFormattedTimestamp(), display.get("timestamp"));
        assertEquals(entry.getReadableTimestamp(zone), display.get("readable"));
    }

    private static AuditLogEntry entry(String user, String action, long timestamp) {
        return new AuditLogEntry(user, action, "jenkins", "management link regression", timestamp);
    }
}
