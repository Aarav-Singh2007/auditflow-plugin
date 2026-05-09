package io.jenkins.plugins.auditlogger;

import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import jenkins.model.Jenkins;

import javax.servlet.ServletContext;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registers a ServletRequestListener to capture HTTP requests in ThreadLocal.
 *
 * Priority: ServletRequestListener fires BEFORE any filter chain, guaranteeing
 * the request is available when SecurityListener.authenticated2() runs.
 * Falls back to PluginServletFilter if listener registration fails
 * (common when Jetty context is already fully initialized).
 */
@Extension
public class AuditRequestCapture {
    private static final Logger LOGGER = Logger.getLogger(AuditRequestCapture.class.getName());
    private static volatile boolean registered = false;

    @Initializer(after = InitMilestone.STARTED)
    public static void register() {
        if (registered) return;
        try {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins == null) return;
            ServletContext ctx = jenkins.servletContext;
            ctx.addListener(new ServletRequestListener() {
                @Override
                public void requestInitialized(ServletRequestEvent sre) {
                    if (sre.getServletRequest() instanceof HttpServletRequest) {
                        RequestHolder.set((HttpServletRequest) sre.getServletRequest());
                    }
                }

                @Override
                public void requestDestroyed(ServletRequestEvent sre) {
                    if (sre.getServletRequest() instanceof HttpServletRequest) {
                        HttpServletRequest req = (HttpServletRequest) sre.getServletRequest();
                        enrichPendingAuthEntry(req);
                        detectAdminAction(req);
                    }
                    RequestHolder.clear();
                }
            });
            registered = true;
            LOGGER.info("AuditRequestCapture: ServletRequestListener registered");
        } catch (IllegalStateException | UnsupportedOperationException e) {
            LOGGER.log(Level.FINE, "ServletRequestListener registration is not supported, using PluginServletFilter fallback", e);
            registerFallbackFilter();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "ServletRequestListener registration failed, using fallback", e);
            registerFallbackFilter();
        }
    }

    private static void registerFallbackFilter() {
        try {
            hudson.util.PluginServletFilter.addFilter(new javax.servlet.Filter() {
                @Override
                public void init(javax.servlet.FilterConfig fc) {}

                @Override
                public void doFilter(javax.servlet.ServletRequest req, javax.servlet.ServletResponse res,
                                     javax.servlet.FilterChain chain)
                        throws java.io.IOException, javax.servlet.ServletException {
                    if (req instanceof HttpServletRequest) {
                        HttpServletRequest httpReq = (HttpServletRequest) req;
                        RequestHolder.set(httpReq);
                        // Capture the authenticated username BEFORE chain.doFilter() —
                        // Jenkins may impersonate SYSTEM during save operations,
                        // but at this point the real user is still in the SecurityContext.
                        String preChainUser = resolveUsername(httpReq);
                        if (preChainUser != null) {
                            RequestHolder.setAuthenticatedUser(preChainUser);
                        }
                    }
                    try {
                        chain.doFilter(req, res);
                    } finally {
                        // Enrich AFTER chain — security chain has already run by the
                        // time PluginServletFilter is invoked, so SecurityContext is
                        // populated and we can resolve the username for pending entries.
                        if (req instanceof HttpServletRequest) {
                            // Re-capture username in case it wasn't resolved before chain
                            if (RequestHolder.getAuthenticatedUser() == null) {
                                String postChainUser = resolveUsername((HttpServletRequest) req);
                                if (postChainUser != null) {
                                    RequestHolder.setAuthenticatedUser(postChainUser);
                                }
                            }
                            enrichPendingAuthEntry((HttpServletRequest) req);
                            detectAdminAction((HttpServletRequest) req);
                        }
                        RequestHolder.clear();
                    }
                }

                @Override
                public void destroy() {}
            });
            registered = true;
            LOGGER.info("AuditRequestCapture: PluginServletFilter fallback registered");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to register fallback filter — IP capture will rely on thread name parsing", e);
        }
    }

    /**
     * Detect critical admin operations by URL pattern on POST requests.
     * Covers: Safe Restart, Plugin Install/Uninstall/Enable/Disable, Security Config.
     */
    private static void detectAdminAction(HttpServletRequest req) {
        try {
            String method = req.getMethod();
            if (!"POST".equalsIgnoreCase(method)) return;

            String uri = req.getRequestURI();
            if (uri == null) return;

            // Normalize: strip context path and trailing slash
            String ctx = req.getContextPath();
            if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
                uri = uri.substring(ctx.length());
            }
            if (uri.endsWith("/")) uri = uri.substring(0, uri.length() - 1);

            AuditLoggerConfiguration config = AuditLoggerConfiguration.get();
            boolean pluginEventsEnabled = config == null || config.isEnablePluginEvents();
            boolean systemConfigEventsEnabled = config != null && config.isEnableSystemConfigEvents();

            String username = resolveUsername(req);
            if (username == null) username = "SYSTEM";

            String action = null;
            String target = null;
            String details = null;
            String severity = "HIGH";

            // Safe Restart / Restart — always captured (audit-critical)
            if ("/safeRestart".equals(uri) || "/restart".equals(uri)
                    || "/manage/safeRestart".equals(uri) || "/manage/restart".equals(uri)) {
                action = "SYSTEM_RESTART";
                target = "Jenkins";
                boolean isSafe = uri.contains("safe");
                details = (isSafe ? "Safe" : "Immediate") + " restart initiated by " + username;
                severity = "CRITICAL";
            }
            // Plugin install — always captured (audit-critical)
            else if (pluginEventsEnabled && isPluginInstallUri(uri)) {
                action = "PLUGIN_INSTALLED";
                target = extractPluginTarget(req, uri);
                details = "Plugin installed: " + target + " by " + username;
            }
            // Plugin uninstall / removal — always captured (audit-critical)
            else if (pluginEventsEnabled && "PLUGIN_REMOVED".equals(classifyPluginAction(uri))) {
                action = "PLUGIN_REMOVED";
                target = extractPluginNameFromUri(uri);
                details = "Plugin removed: " + target + " by " + username;
                severity = "CRITICAL";
            }
            // Plugin update
            else if (pluginEventsEnabled && isPluginUpdateUri(uri)) {
                action = "PLUGIN_UPDATED";
                target = extractPluginTarget(req, uri);
                details = "Plugin updated: " + target + " by " + username;
            }
            // Plugin enable/disable
            else if (pluginEventsEnabled) {
                action = classifyPluginAction(uri);
                if ("PLUGIN_ENABLED".equals(action) || "PLUGIN_DISABLED".equals(action)) {
                    boolean enable = "PLUGIN_ENABLED".equals(action);
                    target = extractPluginNameFromUri(uri);
                    details = "Plugin " + (enable ? "enabled" : "disabled") + ": " + target + " by " + username;
                } else {
                    action = null;
                }
            }
            // Security realm / authorization config changes
            else if (systemConfigEventsEnabled && uri.contains("/configureSecurity")) {
                action = "SECURITY_CONFIG_UPDATED";
                target = "SecurityRealm";
                details = "Security configuration updated by " + username;
                severity = "CRITICAL";
            }
            // Global configuration changes
            else if (systemConfigEventsEnabled
                    && (uri.equals("/manage/configure") || uri.equals("/configSubmit") || uri.equals("/manage/configSubmit"))) {
                action = "GLOBAL_CONFIG_UPDATED";
                target = "GlobalConfig";
                details = "Global configuration updated by " + username;
                severity = "HIGH";
            }
            // Authorization strategy changes
            else if (systemConfigEventsEnabled
                    && (uri.contains("/configureSecurity") || (uri.contains("/manage/") && uri.contains("authorizationStrategy")))) {
                action = "AUTH_STRATEGY_CHANGED";
                target = "AuthorizationStrategy";
                details = "Authorization strategy changed by " + username;
                severity = "CRITICAL";
            }

            if (action != null) {
                AuditLogEntry entry = new AuditLogEntry(username, action, target, details);
                entry.setSeverity(severity);
                AuditLogStorage.getInstance().addEntry(entry);
                LOGGER.log(Level.INFO, "{0}: target={1} by user={2}",
                        new Object[]{action, target, username});
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error detecting admin action", e);
        }
    }

    /** Extract plugin name(s) from install request parameters or URI. */
    private static String extractPluginTarget(HttpServletRequest req, String uri) {
        try {
            // For /pluginManager/installNecessaryPlugins, plugin names in body
            // For /pluginManager/install, check 'pluginName' or 'name' parameter
            String name = req.getParameter("pluginName");
            if (name == null) name = req.getParameter("name");
            if (name != null) return name;
            // Jenkins sends plugin selections as plugin.{name}.default=on
            java.util.Enumeration<String> paramNames = req.getParameterNames();
            if (paramNames != null) {
                StringBuilder plugins = new StringBuilder();
                while (paramNames.hasMoreElements()) {
                    String param = paramNames.nextElement();
                    if (param.startsWith("plugin.") && param.endsWith(".default")) {
                        String pluginName = param.substring("plugin.".length(),
                                param.length() - ".default".length());
                        if (plugins.length() > 0) plugins.append(", ");
                        plugins.append(pluginName);
                    }
                }
                if (plugins.length() > 0) return plugins.toString();
            }
            // For upload: filename from multipart
            if (uri.contains("upload")) return "uploaded-plugin";
        } catch (Exception ignored) {}
        return "plugin(s)";
    }

    static boolean isPluginInstallUri(String uri) {
        return uri.contains("/pluginManager/install") || uri.contains("/pluginManager/uploadPlugin");
    }

    static boolean isPluginUpdateUri(String uri) {
        return uri != null && uri.matches(".*/pluginManager/(deploy|update)$");
    }

    static String classifyPluginAction(String uri) {
        if (uri == null) return null;
        if ((uri.contains("/pluginManager/plugin/") && uri.contains("/uninstall"))
                || (uri.contains("/plugin/") && uri.endsWith("/doUninstall"))) {
            return "PLUGIN_REMOVED";
        }
        if ((uri.contains("/pluginManager/plugin/") || uri.contains("/plugin/"))
                && (uri.contains("/makeEnabled") || uri.endsWith("/enable"))) {
            return "PLUGIN_ENABLED";
        }
        if ((uri.contains("/pluginManager/plugin/") || uri.contains("/plugin/"))
                && (uri.contains("/makeDisabled") || uri.endsWith("/disable"))) {
            return "PLUGIN_DISABLED";
        }
        return null;
    }

    /** Extract plugin short name from URI like /pluginManager/plugin/git/uninstall. */
    static String extractPluginNameFromUri(String uri) {
        // pattern: /pluginManager/plugin/{name}/{action}
        String[] parts = uri.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("plugin".equals(parts[i]) && i + 1 < parts.length) {
                return parts[i + 1];
            }
        }
        return "unknown-plugin";
    }

    /**
     * Enrich a pending auth entry (created by SecurityListener before PluginServletFilter ran)
     * with User-Agent from the now-available HTTP request, then write it to the audit log.
     * For form logins, this runs on the REDIRECT request (the browser follows the 302).
     * For API calls, this runs on the SAME request (filter chain continues after security).
     */
    private static void enrichPendingAuthEntry(HttpServletRequest req) {
        String username = resolveUsername(req);
        if (username == null) return;

        AuditLogEntry entry = RequestHolder.consumePendingAuthEntry(username);
        if (entry == null) return;
        try {
            String ua = req.getHeader("User-Agent");
            if (ua != null) {
                entry.setUserAgent(ua);
                // Update the details string to include the actual UA
                String details = entry.getDetails();
                if (details != null && details.contains("UA: N/A")) {
                    entry.setDetails(details.replace("UA: N/A", "UA: " + shorten(ua)));
                }
            }

            // Re-detect auth method from actual request headers (more accurate than thread name)
            String authHeader = req.getHeader("Authorization");
            if (authHeader != null) {
                String method = null;
                if (authHeader.regionMatches(true, 0, "Basic ", 0, 6)) method = "basic-auth";
                else if (authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) method = "bearer-token";
                else if (authHeader.regionMatches(true, 0, "Negotiate ", 0, 10)) method = "kerberos-spnego";
                if (method != null && entry.getAuthMethod() != null) {
                    entry.setAuthMethod(method);
                }
            }

            AuditLogStorage.getInstance().addEntry(entry);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to enrich pending auth entry", e);
        }
    }

    /** Resolve the authenticated username from the request. */
    private static String resolveUsername(HttpServletRequest req) {
        // 1. Try session-based Spring Security context (preserves real user even in impersonated context)
        try {
            javax.servlet.http.HttpSession session = req.getSession(false);
            if (session != null) {
                Object ctx = session.getAttribute("SPRING_SECURITY_CONTEXT");
                if (ctx != null) {
                    java.lang.reflect.Method getAuth = ctx.getClass().getMethod("getAuthentication");
                    Object auth = getAuth.invoke(ctx);
                    if (auth != null) {
                        java.lang.reflect.Method getName = auth.getClass().getMethod("getName");
                        String name = (String) getName.invoke(auth);
                        if (name != null && !name.isEmpty()
                                && !"anonymous".equalsIgnoreCase(name)
                                && !"SYSTEM".equalsIgnoreCase(name)) {
                            return name;
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
        // 2. Try getRemoteUser()
        String remoteUser = req.getRemoteUser();
        if (remoteUser != null && !remoteUser.isEmpty()
                && !"anonymous".equalsIgnoreCase(remoteUser)) {
            return remoteUser;
        }
        // 3. Try getUserPrincipal()
        Principal p = req.getUserPrincipal();
        if (p != null && p.getName() != null && !p.getName().isEmpty()
                && !"anonymous".equalsIgnoreCase(p.getName())) {
            return p.getName();
        }
        // 4. Try thread-local Spring SecurityContext
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !auth.getName().isEmpty()
                && !"anonymous".equalsIgnoreCase(auth.getName())
                && !"anonymousUser".equals(auth.getName())
                && !"SYSTEM".equalsIgnoreCase(auth.getName())) {
            return auth.getName();
        }
        return null;
    }

    private static String shorten(String s) {
        if (s == null) return "N/A";
        String clean = s.replaceAll("[\\r\\n\\t]", " ");
        return clean.length() > 80 ? clean.substring(0, 80) + "..." : clean;
    }

    /**
     * Safety net: no longer needed since pending entries are in a static map
     * with TTL-based expiry, not a ThreadLocal. Kept as no-op for call sites.
     */
    private static void flushPendingAuthEntry() {
        // No-op — cross-request pending map handles its own expiry
    }
}
