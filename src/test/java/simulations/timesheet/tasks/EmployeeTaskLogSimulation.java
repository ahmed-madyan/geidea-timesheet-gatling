package simulations.timesheet.tasks;

import gatling.assertions.AssertionFactory;
import gatling.config.enums.timesheet.TimesheetBaseUri;
import gatling.utils.HttpProtocolFactory;
import gatling.utils.LoadProfileFactory;
import gatling.utils.PopulationFactory;
import gatling.utils.SimulationFactory;
import groups.timesheet.tasks.EmployeeTaskLogScenario;
import io.gatling.javaapi.core.PopulationBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

public class EmployeeTaskLogSimulation extends Simulation {

    private final HttpProtocolBuilder protocol = new HttpProtocolFactory(TimesheetBaseUri.TIMESHEET)
            .acceptHeader("text/html,application/json")
            .build();

    private final ScenarioBuilder scn = EmployeeTaskLogScenario.buildScenario();

    private final PopulationBuilder population = new SimulationFactory(scn, protocol)
            .injectOpen(LoadProfileFactory.spike(1))
            .build();

    {
        setUp(PopulationFactory.with(population))
                .assertions(AssertionFactory.defaultAssertions());
    }
}
