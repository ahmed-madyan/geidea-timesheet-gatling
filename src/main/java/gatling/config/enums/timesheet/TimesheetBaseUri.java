package gatling.config.enums.timesheet;

import gatling.config.enums.ApiHost;

import java.net.URI;

public enum TimesheetBaseUri implements ApiHost {
    TIMESHEET("https://timesheet.geidea.net");

    private final URI baseURI;

    TimesheetBaseUri(String uri) {
        this.baseURI = URI.create(uri);
    }

    @Override
    public URI uri() {
        return baseURI;
    }

    @Override
    public String toString() {
        return baseURI.toString();
    }
}
