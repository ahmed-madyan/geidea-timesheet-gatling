package gatling.utils;

import io.gatling.javaapi.core.PopulationBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for validating and wrapping PopulationBuilder instances.
 */
public final class PopulationFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(PopulationFactory.class);

    private PopulationFactory() {
        LOGGER.warn("Attempt to instantiate PopulationFactory directly.");
        throw new UnsupportedOperationException("PopulationFactory is a utility class and cannot be instantiated.");
    }

    /**
     * Validates that the provided PopulationBuilder is not null.
     *
     * @param population the PopulationBuilder to validate
     * @return the validated PopulationBuilder
     * @throws IllegalArgumentException if population is null
     */
    public static PopulationBuilder with(PopulationBuilder population) {
        LOGGER.debug("Validating PopulationBuilder instance.");
        if (population == null) {
            String msg = "PopulationBuilder must not be null.";
            LOGGER.error("{}", msg);
            throw new IllegalArgumentException(msg);
        }

        LOGGER.info("PopulationBuilder instance validated successfully.");
        return population;
    }
}