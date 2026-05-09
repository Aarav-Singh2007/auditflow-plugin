package io.jenkins.plugins.auditlogger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class AuditLogStorageTimestampRegressionTest {

    @Test
    public void parseJsonLinePreservesOriginalTimestampAndReadableValue() throws Exception {
        long expectedTimestamp = 1_725_652_800_123L;
        AuditLogEntry original = new AuditLogEntry(
                "regression-user",
                "FAILED_LOGIN",
                "Jenkins",
                "Regression timestamp guard",
                expectedTimestamp);

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(AuditLogEntry.class, new AuditLogEntrySerializer())
                .disableHtmlEscaping()
                .create();

        String json = gson.toJson(original);
        Method parseJsonLine = AuditLogStorage.class.getDeclaredMethod("parseJsonLine", String.class);
        parseJsonLine.setAccessible(true);

        AuditLogEntry restored = (AuditLogEntry) parseJsonLine.invoke(AuditLogStorage.getInstance(), json);

        assertNotNull(restored);
        assertEquals(expectedTimestamp, restored.getTimestamp());
        assertEquals(original.getReadableTimestamp(), restored.getReadableTimestamp());
    }
}