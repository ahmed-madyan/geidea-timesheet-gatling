package monitoring;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Resolves the Gatling report folder name used for {@code logs/<simId>-<runTimestamp>.log}.
 */
public final class SimulationNameResolver {

    private SimulationNameResolver() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Basename of the current run's report folder, e.g.
     * {@code employeetasklogsimulation-20260928104232564}.
     *
     * @param minMtimeForSniff ignore report folders older than this instant
     */
    public static String resolveReportFolderBasename(long minMtimeForSniff) {
        File latest = findLatestReportDir(new File("reports"), minMtimeForSniff);
        if (latest == null) {
            return null;
        }
        String folder = latest.getName();
        return isGatlingReportFolderName(folder) ? folder : null;
    }

    static boolean isGatlingReportFolderName(String folder) {
        int dash = folder.lastIndexOf('-');
        if (dash <= 0) {
            return false;
        }
        return folder.substring(dash + 1).chars().allMatch(Character::isDigit);
    }

    static File findLatestReportDir(File reports, long minMtime) {
        if (!reports.isDirectory()) {
            return null;
        }
        File[] dirs = reports.listFiles(File::isDirectory);
        if (dirs == null || dirs.length == 0) {
            return null;
        }
        return Arrays.stream(dirs)
                .filter(dir -> dir.lastModified() >= minMtime)
                .filter(dir -> isGatlingReportFolderName(dir.getName()))
                .max(Comparator.comparingLong(File::lastModified))
                .orElse(null);
    }
}
