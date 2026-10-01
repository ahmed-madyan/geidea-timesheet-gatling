package groups.timesheet.tasks;

import gatling.endpoints.timesheet.tasks.TaskLogChain;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;

import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.scenario;

public final class EmployeeTaskLogScenario {

    public static final String SCENARIO_NAME = "Employee logs every task variety";

    private EmployeeTaskLogScenario() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static ChainBuilder build() {
        return exec(TaskLogChain.build());
    }

    public static ScenarioBuilder buildScenario() {
        return scenario(SCENARIO_NAME).exec(build());
    }
}
