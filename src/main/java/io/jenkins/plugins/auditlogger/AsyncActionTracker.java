package io.jenkins.plugins.auditlogger;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tracks recent CLI executions to correlate background SYSTEM actions back to the initiating user.
 * Keeps entries for a short time window (TTL) and resolves them via exact or partial arg matching,
 * prioritizing correctness over coverage (ambiguous matches return null).
 */
public class AsyncActionTracker {
    private static final Logger LOGGER = Logger.getLogger(AsyncActionTracker.class.getName());
    private static final AsyncActionTracker INSTANCE = new AsyncActionTracker();
    private static final long TTL_MS = 10000; // 10 seconds

    private final ConcurrentLinkedQueue<CliAction> recentActions = new ConcurrentLinkedQueue<>();

    private AsyncActionTracker() {}

    public static AsyncActionTracker getInstance() { return INSTANCE; }

    public void register(String username, String command, List<String> args, long timestamp) {
        cleanExpired(System.currentTimeMillis());
        recentActions.add(new CliAction(username, command, args, timestamp));
        LOGGER.log(Level.FINE, "Registered CLI action for {0}: {1}", new Object[]{username, command});
    }

    public String resolveUser(String affectedObject, long now) {
        cleanExpired(now);
        String matchedUser = null;

        for (CliAction action : recentActions) {
            if (action.timestamp > now - TTL_MS) {
                if (matches(action, affectedObject)) {
                    if (matchedUser != null && !matchedUser.equals(action.username)) {
                        LOGGER.log(Level.FINE, "Ambiguous match for {0}, returning null", affectedObject);
                        return null; // Ambiguous match -> give up
                    }
                    matchedUser = action.username;
                }
            }
        }
        return matchedUser;
    }

    private boolean matches(CliAction action, String affectedObject) {
        if (affectedObject == null) return false;
        String lowerAffected = affectedObject.toLowerCase(java.util.Locale.ENGLISH);
        for (String arg : action.args) {
            if (arg != null && !arg.isEmpty() && lowerAffected.contains(arg.toLowerCase(java.util.Locale.ENGLISH))) {
                return true;
            }
        }
        return false;
    }

    private void cleanExpired(long now) {
        recentActions.removeIf(action -> action.timestamp <= now - TTL_MS);
    }

    private static class CliAction {
        final String username;
        final String command;
        final List<String> args;
        final long timestamp;

        CliAction(String username, String command, List<String> args, long timestamp) {
            this.username = username;
            this.command = command;
            this.args = args;
            this.timestamp = timestamp;
        }
    }
}
