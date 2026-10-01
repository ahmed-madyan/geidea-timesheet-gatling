package monitoring;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;

/**
 * Logback {@link FileAppender} that names each run's log file after the matching Gatling
 * report folder: {@code logs/<simId>-<runTimestamp>.log} (same basename as
 * {@code reports/<simId>-<runTimestamp>/}).
 *
 * <p>File creation is deferred until the first log event because Logback starts before
 * Gatling creates the report folder under {@code reports/}.
 */
public class SimulationNamedFileAppender extends FileAppender<ILoggingEvent> {

    private String logDir = "logs";
    private volatile boolean fileOpened;
    private final long startedAtMs = System.currentTimeMillis();

    @Override
    public void start() {
        if (getEncoder() == null) {
            addError("No encoder set for SimulationNamedFileAppender");
            return;
        }
        if (!getEncoder().isStarted()) {
            getEncoder().start();
        }
        started = true;
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (!fileOpened) {
            synchronized (this) {
                if (!fileOpened && tryOpenLogFile()) {
                    fileOpened = true;
                }
            }
        }
        if (!fileOpened) {
            return;
        }
        super.append(eventObject);
    }

    private boolean tryOpenLogFile() {
        String reportBasename = SimulationNameResolver.resolveReportFolderBasename(startedAtMs);
        if (reportBasename == null || reportBasename.isBlank()) {
            return false;
        }
        setFile(logDir + "/" + reportBasename + ".log");
        try {
            openFile(getFile());
        } catch (java.io.IOException e) {
            addError("Failed to open simulation log file: " + getFile(), e);
            return false;
        }
        addInfo("Simulation log file: " + getFile());
        return true;
    }

    @Override
    public void stop() {
        if (fileOpened) {
            super.stop();
        } else {
            started = false;
        }
    }

    public void setLogDir(String logDir) {
        this.logDir = logDir;
    }
}
