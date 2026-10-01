package gatling.endpoints.timesheet.approval;

import gatling.builders.ChainBuilderFactory;
import gatling.config.enums.timesheet.TimesheetBasePath;
import gatling.endpoints.timesheet.TimesheetPage;
import gatling.endpoints.timesheet.auth.TimesheetLoginChain;
import gatling.utils.RetryFactory;
import io.gatling.javaapi.core.ChainBuilder;

import java.util.List;

import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.http.HttpDsl.status;

public final class BulkApproveChain {

    private BulkApproveChain() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static ChainBuilder openApproval() {
        String path = TimesheetBasePath.resolve(TimesheetBasePath.PENDING_TASKS, "timesheet.pendingTasksPath");
        return TimesheetPage.get(
                "Timesheet | Open approvals",
                path,
                "input[name='__RequestVerificationToken']");
    }

    public static ChainBuilder listPending() {
        String path = TimesheetBasePath.resolve(TimesheetBasePath.PENDING_TASKS, "timesheet.pendingTasksPath");
        return new ChainBuilderFactory("Timesheet | List pending tasks")
                .get(path + "?handler=PendingTasks&Criteria.PageIndex=1&Criteria.PageSize=100")
                .withHeader("Accept", "application/json")
                .disableFollowRedirect()
                .withCheck(status().is(200).saveAs("lastStatus"))
                .withCheck(jsonPath("$.list.totalItems").saveAs("pendingCount"))
                .withCheck(jsonPath("$.list.items[*].logId").findAll().optional().saveAs("ids"))
                .withCheck(jsonPath("$.list.items[*].hours").findAll().optional().saveAs("hours"))
                .build();
    }

    public static ChainBuilder approve() {
        String path = TimesheetBasePath.resolve(TimesheetBasePath.BULK_APPROVE, "timesheet.bulkApprovePath");
        return new ChainBuilderFactory("Timesheet | Bulk approve")
                .post(path + "?handler=BulkApprove")
                .withHeader("Content-Type", "application/json")
                .withHeader("RequestVerificationToken", "#{csrf}")
                .withDynamicBody(BulkApproveChain::approveBody)
                .disableFollowRedirect()
                .withCheck(status().is(200).saveAs("lastStatus"))
                .withCheck(jsonPath("$.success").ofBoolean().is(true))
                .build();
    }

    static String approveBody(io.gatling.javaapi.core.Session session) {
        List<String> ids = session.getList("ids");
        List<String> hours = session.getList("hours");
        StringBuilder body = new StringBuilder("{\"tasks\":[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                body.append(',');
            }
            body.append("{\"logId\":").append(ids.get(i))
                    .append(",\"hours\":").append(hours.get(i))
                    .append('}');
        }
        body.append("]}");
        return body.toString();
    }

    public static ChainBuilder build() {
        return exec(
                TimesheetLoginChain.build(),
                RetryFactory.withRetry("openApproval", openApproval()),
                RetryFactory.withRetry("listPending", listPending()))
                .asLongAs(session -> Integer.parseInt(session.getString("pendingCount")) > 0)
                .on(exec(
                        RetryFactory.withRetry("bulkApprove", approve()),
                        RetryFactory.withRetry("listPending", listPending())));
    }
}
