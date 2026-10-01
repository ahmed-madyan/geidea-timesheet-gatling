package gatling.config.enums.timesheet;

import gatling.config.enums.ApiPath;

import lombok.Getter;

/**
 * Timesheet routes. Login is the public ASP.NET form.
 * Task and approval paths default to placeholders and should be overridden with
 * {@code -Dtimesheet.taskLogPath}, {@code -Dtimesheet.pendingTasksPath}, and
 * {@code -Dtimesheet.bulkApprovePath} after a browser capture.
 */
@Getter
public enum TimesheetBasePath implements ApiPath {
    LOGIN("/"),
    TASK_LOG("/TimesheetEntry"),
    PENDING_TASKS("/TimesheetApproval"),
    BULK_APPROVE("/TimesheetApproval");

    private final String value;

    TimesheetBasePath(String value) {
        this.value = value;
    }

    @Override
    public String path() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    public static String resolve(TimesheetBasePath fallback, String property) {
        String override = System.getProperty(property, "");
        return override.isBlank() ? fallback.path() : override;
    }
}
