package gatling.endpoints.timesheet.auth;

import gatling.builders.ChainBuilderFactory;
import gatling.config.enums.timesheet.TimesheetBasePath;
import gatling.endpoints.timesheet.TimesheetPage;
import gatling.utils.FeederFactory;
import gatling.utils.RetryFactory;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

import static io.gatling.javaapi.core.CoreDsl.bodyString;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.feed;
import static io.gatling.javaapi.http.HttpDsl.header;
import static io.gatling.javaapi.http.HttpDsl.status;

public final class TimesheetLoginChain {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimesheetLoginChain.class);
    private static final String USERS = "data/timesheet/users.csv";

    private TimesheetLoginChain() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static ChainBuilder openLogin() {
        return TimesheetPage.get(
                "Timesheet | Open login",
                TimesheetBasePath.LOGIN.path(),
                "input[name='__RequestVerificationToken']");
    }

    public static ChainBuilder signIn() {
        return new ChainBuilderFactory("Timesheet | Sign in")
                .post(TimesheetBasePath.LOGIN)
                .withHeader("Content-Type", "application/x-www-form-urlencoded")
                .withBody("Username=#{username}&Password=#{password}&__RequestVerificationToken=#{csrf}")
                .disableFollowRedirect()
                .withCheck(status().saveAs("lastStatus"))
                .withCheck(status().is(302))
                .withCheck(header("Location").optional().saveAs("signedInLocation"))
                .withCheck(bodyString().optional().saveAs("loginPage"))
                .build();
    }

    public static ChainBuilder build() {
        return exec(
                feed(FeederFactory.csvFile(USERS)),
                RetryFactory.withRetry("openLogin", openLogin(), 2, 0),
                signIn(),
                exec(TimesheetLoginChain::requireSignedIn))
                .exitHereIfFailed();
    }

    /** A rejected password stays on the login page. Say so instead of retrying the same sign-in. */
    static Session requireSignedIn(Session session) {
        int status = session.contains("lastStatus") ? session.getInt("lastStatus") : 0;
        String location = session.contains("signedInLocation") ? session.getString("signedInLocation") : "";
        if (status == 302 && !location.toLowerCase().contains("login")) {
            LOGGER.info("Signed in as {}", session.getString("username"));
            return session;
        }
        String username = session.contains("username") ? session.getString("username") : "(missing username)";
        String message = "Sign-in failed for " + username + ". Check the timesheet username and password.";
        LOGGER.error("{} (HTTP {}, location {})", message, status, location);
        try {
            Path summary = Path.of("logs", "timesheet-summary.txt");
            Files.createDirectories(summary.getParent());
            Files.writeString(summary, message + System.lineSeparator());
        } catch (Exception writeError) {
            LOGGER.error("Could not write the sign-in failure: {}", writeError.toString());
        }
        throw new IllegalStateException(message);
    }
}
