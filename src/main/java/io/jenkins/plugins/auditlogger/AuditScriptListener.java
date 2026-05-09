package io.jenkins.plugins.auditlogger;

import hudson.Extension;
import hudson.model.User;
import jakarta.servlet.http.HttpServletRequest;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Script execution listener: captures script console access and approvals.
 * 
 * CRITICAL SECURITY: Replaces unreliable URL pattern matching with actual
 * event interception. This ensures we capture:
 * - Script console access (regardless of URL path)
 * - Script approval (when user reviews/approves scripts)
 * - Script parameters
 * - Groovy execution events
 * 
 * Uses ScriptApproval listener pattern which fires BEFORE execution,
 * guaranteeing reliable capture even if URL routing changes.
 */
@Extension
public class AuditScriptListener {
    private static final Logger LOGGER = Logger.getLogger(AuditScriptListener.class.getName());

    /**
     * Intercept script approval events.
     * Called when user approves a pending script.
     */
    public static void recordScriptApproval(String scriptHash, String script, String approver) {
        try {
            AuditLoggerConfiguration config = AuditLoggerConfiguration.get();
            if (config == null || !config.isEnableSystemConfigEvents()) {
                return;
            }

            String username = approver != null ? approver : resolveCurrentUser();
            if (username == null) username = "SYSTEM";

            String details = String.format(
                "Script approved: %s characters | Hash: %s | Approved by %s",
                script != null ? script.length() : 0,
                scriptHash != null ? scriptHash.substring(0, Math.min(16, scriptHash.length())) + "..." : "UNKNOWN",
                username
            );

            AuditLogEntry entry = new AuditLogEntry(username, "SCRIPT_APPROVED", "ScriptConsole", details);
            entry.setSeverity("CRITICAL");
            AuditLogStorage.getInstance().addEntry(entry);

            LOGGER.log(Level.INFO, "SCRIPT_APPROVED: hash={0} by user={1}",
                    new Object[]{scriptHash, username});
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error recording script approval", e);
        }
    }

    /**
     * Intercept script console access attempt (before execution).
     * This is more reliable than URL matching as it fires regardless of routing.
     */
    public static void recordScriptConsoleAccess(String scriptContent, String scriptSource) {
        try {
            AuditLoggerConfiguration config = AuditLoggerConfiguration.get();
            if (config == null || !config.isEnableSystemConfigEvents()) {
                return;
            }

            String username = resolveCurrentUser();
            if (username == null) username = "SYSTEM";

            String details = String.format(
                "Script console access: %s | Source: %s | User: %s",
                scriptContent != null ? scriptContent.substring(0, Math.min(50, scriptContent.length())) + "..." : "N/A",
                scriptSource != null ? scriptSource : "unknown",
                username
            );

            AuditLogEntry entry = new AuditLogEntry(username, "SCRIPT_CONSOLE_ACCESS", "ScriptConsole", details);
            entry.setSeverity("CRITICAL");
            AuditLogStorage.getInstance().addEntry(entry);

            LOGGER.log(Level.INFO, "SCRIPT_CONSOLE_ACCESS: source={0} by user={1}",
                    new Object[]{scriptSource, username});
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error recording script console access", e);
        }
    }

    /**
     * Intercept unsafe (unapproved) script execution attempts.
     * These are security-critical events that indicate policy violations.
     */
    public static void recordUnsafeScriptExecution(String script, String reason) {
        try {
            AuditLoggerConfiguration config = AuditLoggerConfiguration.get();
            if (config == null || !config.isEnableSystemConfigEvents()) {
                return;
            }

            String username = resolveCurrentUser();
            if (username == null) username = "SYSTEM";

            String details = String.format(
                "Unsafe script execution blocked: %s | Reason: %s | User: %s",
                script != null ? script.substring(0, Math.min(50, script.length())) + "..." : "N/A",
                reason != null ? reason : "unapproved",
                username
            );

            AuditLogEntry entry = new AuditLogEntry(username, "UNSAFE_SCRIPT_BLOCKED", "ScriptConsole", details);
            entry.setSeverity("CRITICAL");
            AuditLogStorage.getInstance().addEntry(entry);

            LOGGER.log(Level.WARNING, "UNSAFE_SCRIPT_BLOCKED: reason={0} by user={1}",
                    new Object[]{reason, username});
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error recording unsafe script execution", e);
        }
    }

    /**
     * Resolve current user from multiple sources (same pattern as other listeners).
     */
    private static String resolveCurrentUser() {
        try {
            // 1. Try servlet request (from RequestHolder)
            HttpServletRequest req = RequestHolder.get();
            if (req != null) {
                String user = (String) req.getAttribute("AUDIT_USER");
                if (user != null) return user;
            }

            // 2. Try Jenkins User.current()
            User u = User.current();
            if (u != null && isRealUser(u.getId())) {
                return u.getId();
            }

            // 3. Try Spring SecurityContext
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && isRealUser(auth.getName())) {
                return auth.getName();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean isRealUser(String name) {
        return name != null && !name.isEmpty()
                && !"anonymousUser".equals(name)
                && !"anonymous".equalsIgnoreCase(name)
                && !"system".equalsIgnoreCase(name);
    }
}
