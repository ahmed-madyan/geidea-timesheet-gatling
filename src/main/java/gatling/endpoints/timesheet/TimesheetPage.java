package gatling.endpoints.timesheet;

import gatling.builders.ChainBuilderFactory;
import io.gatling.javaapi.core.ChainBuilder;

import static io.gatling.javaapi.core.CoreDsl.css;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Loads a timesheet page on the public host and keeps the antiforgery token.
 * Redirects are not followed. An unauthenticated call redirects to an internal
 * {@code /Login} address that does not exist on timesheet.geidea.net.
 */
public final class TimesheetPage {

    private TimesheetPage() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static ChainBuilder get(String name, String path, String csrfSelector) {
        return new ChainBuilderFactory(name)
                .get(path)
                .disableFollowRedirect()
                .withCheck(status().is(200))
                .withCheck(css(csrfSelector, "value").saveAs("csrf"))
                .build();
    }
}
