package io.jenkins.plugins.auditlogger;

import hudson.Extension;
import jenkins.cli.listeners.CLIContext;
import jenkins.cli.listeners.CLIListener;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Audits CLI execution from Jenkins' core CLIListener callback.
 */
@Extension
public class AuditCLIListener implements CLIListener {
    private static final Logger LOGGER = Logger.getLogger(AuditCLIListener.class.getName());
    static final String CLI_EXECUTION_ACTION = "CLI_EXECUTION";

    @Override
    public void onCompleted(CLIContext context, int exitCode) {
        try {
            AuditLoggerConfiguration config = AuditLoggerConfiguration.get();
            if (config == null || !config.isEnableSystemConfigEvents()) {
                return;
            }

            String username = resolveCurrentUser(context);

            String command = context.getCommand();
            String details = "CLI Command: " + command + " | Exit Code: " + exitCode +
                    (context.getArgs() != null && !context.getArgs().isEmpty() ? " | Args: " + context.getArgs() : "");

            String target = "CLI: " + command;
            AuditLogEntry entry = new AuditLogEntry(username, CLI_EXECUTION_ACTION, target, details);
            entry.setSeverity(exitCode == 0 ? "LOW" : "MEDIUM");

            AuditLogStorage.getInstance().addEntry(entry);

            LOGGER.log(Level.INFO, "CLI_EXECUTION: target={0}, exitCode={1}, user={2}",
                    new Object[]{target, exitCode, username});

            
            AsyncActionTracker.getInstance().register(username, command, context.getArgs(), System.currentTimeMillis());
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error recording CLI execution", e);
        }
    }

    private String resolveCurrentUser(CLIContext context) {
        var auth = context.getAuth();
        if (auth != null) {
            String name = auth.getName();
            if (name != null && !name.isEmpty() && !"anonymousUser".equals(name) && !"anonymous".equalsIgnoreCase(name)) {
                return name;
            }
        }
        return "SYSTEM";
    }
}
