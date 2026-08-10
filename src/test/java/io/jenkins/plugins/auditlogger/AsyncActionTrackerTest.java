package io.jenkins.plugins.auditlogger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class AsyncActionTrackerTest {

    private AsyncActionTracker tracker;

    @BeforeEach
    public void setup() {
        tracker = AsyncActionTracker.getInstance();
        // Clear tracker state
        tracker.resolveUser(null, Long.MAX_VALUE); // effectively clears all
    }

    @Test
    public void testResolveUserExactMatch() {
        long now = System.currentTimeMillis();
        tracker.register("userA", "install-plugin", Arrays.asList("ldap", "-deploy"), now - 1000);
        
        String user = tracker.resolveUser("ldap", now);
        assertEquals("userA", user);
    }

    @Test
    public void testResolveUserPartialMatch() {
        long now = System.currentTimeMillis();
        tracker.register("userB", "create-job", Arrays.asList("my-project"), now - 2000);
        
        // e.g. the Saveable is called "my-project"
        String user = tracker.resolveUser("my-project", now);
        assertEquals("userB", user);
    }

    @Test
    public void testResolveUserExpired() {
        long now = System.currentTimeMillis();
        // Register an event that is 11 seconds old (TTL is 10s)
        tracker.register("userC", "install-plugin", Arrays.asList("git"), now - 11000);
        
        String user = tracker.resolveUser("git", now);
        assertNull(user);
    }

    @Test
    public void testResolveUserAmbiguousMatch() {
        long now = System.currentTimeMillis();
        tracker.register("user1", "install-plugin", Arrays.asList("ldap"), now - 1000);
        tracker.register("user2", "install-plugin", Arrays.asList("ldap"), now - 500);
        
        String user = tracker.resolveUser("ldap", now);
        assertNull(user, "Ambiguous match should return null");
    }

    @Test
    public void testResolveUserNoMatch() {
        long now = System.currentTimeMillis();
        tracker.register("userA", "install-plugin", Arrays.asList("ldap"), now - 1000);
        
        String user = tracker.resolveUser("git", now);
        assertNull(user);
    }
}
