package gatling.utils;

import io.gatling.javaapi.core.ClosedInjectionStep;
import io.gatling.javaapi.core.OpenInjectionStep;
import io.gatling.javaapi.core.PopulationBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory class to construct a complete Gatling PopulationBuilder with
 * scenario, protocol, and injection steps.
 *
 * <p>On {@link #build()}, the HTTP protocol is always given a shared connection
 * pool sized to the peak VU count recorded by {@link LoadProfileFactory}. That
 * is what actually delivers the target concurrent users to the server (Gatling's
 * default is 6 connections per host).
 */
public class SimulationFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimulationFactory.class);

    private ScenarioBuilder scenario;
    private final HttpProtocolBuilder protocol;

    private List<OpenInjectionStep> openSteps = new ArrayList<>();
    private List<ClosedInjectionStep> closedSteps = new ArrayList<>();
    private int peakConcurrentUsers;

    /**
     * Constructs the factory with required scenario and protocol.
     *
     * @param scenario the ScenarioBuilder
     * @param protocol the HttpProtocolBuilder
     * @throws IllegalArgumentException if either argument is null
     */
    public SimulationFactory(ScenarioBuilder scenario, HttpProtocolBuilder protocol) {
        if (scenario == null || protocol == null) {
            String msg = "Scenario and protocol must not be null.";
            LOGGER.error("{}", msg);
            throw new IllegalArgumentException(msg);
        }

        this.scenario = scenario;
        this.protocol = protocol;
        LOGGER.info("SimulationFactory initialized with valid scenario and protocol.");
    }

    /**
     * Sets the open model injection steps (e.g., at-once, ramp-up).
     * If previously defined, the list will be replaced.
     *
     * @param steps array of OpenInjectionStep
     * @return this instance for fluent API
     */
    public SimulationFactory injectOpen(OpenInjectionStep... steps) {
        if (steps == null || steps.length == 0) {
            LOGGER.warn("No open injection steps provided. Injection not set.");
            return this;
        }
        this.openSteps = Arrays.asList(steps);
        this.closedSteps.clear();
        capturePeakFromProfiles();
        LOGGER.info("Open injection configured with {} step(s).", openSteps.size());
        return this;
    }

    /**
     * Holds {@code profile.threads()} in-flight VUs until {@code targetedSuccess()}
     * completions. Uses closed injection so the server keeps receiving that many
     * connections; the run stops on the success target, not a duration.
     */
    public SimulationFactory injectOpen(LoadProfileFactory.ConcurrentUntilSuccess profile) {
        return injectUntilSuccess(profile);
    }

    /**
     * Sets the closed model injection steps (e.g., constant concurrent users).
     * If previously defined, the list will be replaced.
     *
     * @param steps array of ClosedInjectionStep
     * @return this instance for fluent API
     */
    public SimulationFactory injectClosed(ClosedInjectionStep... steps) {
        if (steps == null || steps.length == 0) {
            LOGGER.warn("No closed injection steps provided. Injection not set.");
            return this;
        }
        this.closedSteps = Arrays.asList(steps);
        this.openSteps.clear();
        capturePeakFromProfiles();
        LOGGER.info("Closed injection configured with {} step(s).", closedSteps.size());
        return this;
    }

    /**
     * Same as {@link #injectOpen(LoadProfileFactory.ConcurrentUntilSuccess)}.
     */
    public SimulationFactory injectClosed(LoadProfileFactory.ConcurrentUntilSuccess profile) {
        return injectUntilSuccess(profile);
    }

    private SimulationFactory injectUntilSuccess(LoadProfileFactory.ConcurrentUntilSuccess profile) {
        if (profile == null) {
            throw new IllegalArgumentException("concurrentUntilSuccess profile must not be null");
        }
        this.scenario = profile.wrap(this.scenario);
        LOGGER.info(
                "concurrentUntilSuccess: holding {} concurrent VUs until {} successes "
                        + "(closed injector; stop on target, not duration).",
                profile.threads(), profile.targetedSuccess());
        return injectClosed(profile.closedInjection());
    }

    /**
     * Builds the final PopulationBuilder with configured scenario, injection, and protocol.
     *
     * @return a fully configured PopulationBuilder
     * @throws IllegalStateException if no injection steps are defined
     */
    public PopulationBuilder build() {
        LOGGER.info("Building PopulationBuilder...");

        PopulationBuilder builder;

        if (!openSteps.isEmpty()) {
            builder = scenario.injectOpen(openSteps.toArray(new OpenInjectionStep[0]));
            LOGGER.info("Using open model with {} step(s).", openSteps.size());
        } else if (!closedSteps.isEmpty()) {
            builder = scenario.injectClosed(closedSteps.toArray(new ClosedInjectionStep[0]));
            LOGGER.info("Using closed model with {} step(s).", closedSteps.size());
        } else {
            String msg = "No injection steps configured. Cannot build simulation.";
            LOGGER.error("{}", msg);
            throw new IllegalStateException(msg);
        }

        return builder.protocols(protocolWithGuaranteedConnections());
    }

    private void capturePeakFromProfiles() {
        int taken = LoadProfileFactory.takePeakConcurrentUsers();
        if (taken > 0) {
            peakConcurrentUsers = Math.max(peakConcurrentUsers, taken);
        }
    }

    /**
     * Shared pool sized to the injection's peak VU count so every concurrent user
     * can open a socket. Gatling's HTTP/1.1 default is 6 connections per host.
     */
    private HttpProtocolBuilder protocolWithGuaranteedConnections() {
        int pool = peakConcurrentUsers > 0
                ? peakConcurrentUsers
                : HttpProtocolFactory.DEFAULT_SHARED_MAX_CONNECTIONS;
        LOGGER.info("Guaranteeing {} shared HTTP connections for target concurrent users.", pool);
        return protocol.shareConnections().maxConnectionsPerHost(pool);
    }
}
