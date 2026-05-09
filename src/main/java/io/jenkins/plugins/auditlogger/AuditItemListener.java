package io.jenkins.plugins.auditlogger;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.User;
import hudson.model.listeners.ItemListener;
import org.kohsuke.stapler.Stapler;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Job lifecycle listener: created, deleted, updated, renamed, copied, moved.
 */
@Extension
public class AuditItemListener extends ItemListener {
    private static final Logger LOGGER = Logger.getLogger(AuditItemListener.class.getName());

    @Override
    public void onCreated(Item item) {
        String user = currentUser();
        log("JOB_CREATED", item.getFullName(),
                String.format("Job created: %s (type: %s) by %s", item.getFullName(), item.getClass().getSimpleName(), user));
    }

    @Override
    public void onDeleted(Item item) {
        String user = currentUser();
        log("JOB_DELETED", item.getFullName(),
                String.format("Job deleted: %s by %s", item.getFullName(), user));
    }

    @Override
    public void onRenamed(Item item, String oldName, String newName) {
        String user = currentUser();
        log("JOB_RENAMED", item.getFullName(),
                String.format("Job renamed from '%s' to '%s' by %s", oldName, newName, user));
    }

    @Override
    public void onCopied(Item src, Item copy) {
        String user = currentUser();
        log("JOB_COPIED", copy.getFullName(),
                String.format("Job copied from '%s' by %s", src.getFullName(), user));
    }

    @Override
    public void onLocationChanged(Item item, String oldFullName, String newFullName) {
        String user = currentUser();
        log("JOB_MOVED", newFullName,
                String.format("Job moved from '%s' by %s", oldFullName, user));
    }

    private void log(String action, String target, String details) {
        try {
            AuditLoggerConfiguration config = AuditLoggerConfiguration.get();
            if (config != null && !config.isEnableJobConfigEvents()) return;

            if (StartupPhaseManager.isInStartupGracePeriod()) {
                LOGGER.log(Level.FINE, "Suppressing startup-phase job event: {0} on {1}",
                        new Object[]{action, target});
                return;
            }

            String username = currentUser();

            // Suppress all SYSTEM-initiated item events (branch indexing, SCM polling,
            // auto-discovery) — not relevant for compliance auditing.
            if ("SYSTEM".equals(username)) {
                LOGGER.log(Level.FINE, "Suppressing SYSTEM item event: {0} on {1}",
                        new Object[]{action, target});
                return;
            }

            AuditLogStorage.getInstance().addEntry(
                    new AuditLogEntry(username, action, target, details));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error recording item event: " + action, e);
        }
    }

    private static String currentUser() {
        // 1. Try session-based Spring Security context first — preserves original
        //    logged-in user even when Jenkins impersonates SYSTEM internally
        try {
            jakarta.servlet.http.HttpServletRequest req = RequestHolder.get();
            if (req != null) {
                jakarta.servlet.http.HttpSession session = req.getSession(false);
                if (session != null) {
                    Object ctx = session.getAttribute("SPRING_SECURITY_CONTEXT");
                    if (ctx != null) {
                        java.lang.reflect.Method getAuth = ctx.getClass().getMethod("getAuthentication");
                        Object auth = getAuth.invoke(ctx);
                        if (auth != null) {
                            java.lang.reflect.Method getName = auth.getClass().getMethod("getName");
                            String name = (String) getName.invoke(auth);
                            if (isRealUser(name)) return name;
                        }
                    }
                }
                String remoteUser = req.getRemoteUser();
                if (isRealUser(remoteUser)) return remoteUser;
                java.security.Principal p = req.getUserPrincipal();
                if (p != null && isRealUser(p.getName())) return p.getName();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
        // 2. Try Stapler request
        try {
            org.kohsuke.stapler.StaplerRequest2 req = Stapler.getCurrentRequest2();
            if (req != null) {
                String remoteUser = req.getRemoteUser();
                if (isRealUser(remoteUser)) return remoteUser;
                java.security.Principal p = req.getUserPrincipal();
                if (p != null && isRealUser(p.getName())) return p.getName();
            }
        } catch (RuntimeException ignored) {}
        // 3. Try Jenkins User.current()
        try {
            User u = User.current();
            if (u != null && isRealUser(u.getId())) return u.getId();
        } catch (RuntimeException ignored) {}
        // 4. Try Spring SecurityContext (thread-local)
        try {
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && isRealUser(auth.getName())) {
                return auth.getName();
            }
        } catch (RuntimeException ignored) {}
        return "SYSTEM";
    }

    private static boolean isRealUser(String name) {
        return name != null && !name.isEmpty()
                && !"SYSTEM".equalsIgnoreCase(name)
                && !"anonymous".equalsIgnoreCase(name)
                && !"anonymousUser".equals(name);
    }
}
