package groups.timesheet.approval;

import gatling.endpoints.timesheet.approval.BulkApproveChain;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;

import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.scenario;

public final class ManagerBulkApproveScenario {

    public static final String SCENARIO_NAME = "Manager bulk-approves team tasks";

    private ManagerBulkApproveScenario() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static ChainBuilder build() {
        return exec(BulkApproveChain.build());
    }

    public static ScenarioBuilder buildScenario() {
        return scenario(SCENARIO_NAME).exec(build());
    }
}
