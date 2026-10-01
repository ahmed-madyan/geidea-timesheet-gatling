package gatling.utils;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.gatling.javaapi.core.CoreDsl.scenario;

/**
 * Factory class to build a Gatling ScenarioBuilder with an ordered list of ChainBuilder steps.
 */
public class ScenarioFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScenarioFactory.class);

    /**
     * -- GETTER --
     *  Returns the scenario name.
     *
     * @return name of the scenario
     */
    @Getter
    private final String scenarioName;
    private final List<ChainBuilder> chainSteps = new ArrayList<>();

    /**
     * Constructor requiring a scenario name.
     *
     * @param scenarioName name of the scenario
     * @throws IllegalArgumentException if name is null or blank
     */
    public ScenarioFactory(String scenarioName) {
        if (scenarioName == null || scenarioName.isBlank()) {
            LOGGER.error("Scenario name must not be null or blank.");
            throw new IllegalArgumentException("Scenario name is required.");
        }

        this.scenarioName = scenarioName;
        LOGGER.info("Initialized ScenarioFactory with scenario name: {}", scenarioName);
    }

    /**
     * Appends one or more ChainBuilder steps to the scenario.
     *
     * @param chains varargs list of ChainBuilder instances
     * @return this instance for fluent API usage
     */
    public ScenarioFactory execChain(ChainBuilder... chains) {
        if (chains == null || chains.length == 0) {
            LOGGER.warn("No ChainBuilder steps provided to execChain; operation skipped.");
            return this;
        }

        chainSteps.addAll(Arrays.asList(chains));
        LOGGER.info("Added {} chain step(s) to scenario: {}", chains.length, scenarioName);
        return this;
    }

    /**
     * Builds and returns the ScenarioBuilder instance with all chained steps.
     *
     * @return the fully constructed ScenarioBuilder
     */
    public ScenarioBuilder build() {
        if (chainSteps.isEmpty()) {
            LOGGER.warn("Building scenario with no chain steps: {}", scenarioName);
        }

        ScenarioBuilder builder = scenario(scenarioName);
        for (ChainBuilder chain : chainSteps) {
            builder = builder.exec(chain);
        }

        LOGGER.info("Scenario \"{}\" built with {} step(s).", scenarioName, chainSteps.size());
        return builder;
    }

    /**
     * Exposes an unmodifiable list of configured chain steps.
     *
     * @return list of chain steps
     */
    public List<ChainBuilder> getChainSteps() {
        return Collections.unmodifiableList(chainSteps);
    }

}