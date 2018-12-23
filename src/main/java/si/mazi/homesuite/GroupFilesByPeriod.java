package si.mazi.homesuite;

import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

import static si.mazi.homesuite.Utils.Action.MOVE;
import static si.mazi.homesuite.Utils.checkDir;

/**
 * Copies and groups files from source directories into a subdirectory structure in the destination directory, where Names of subdirectories
 * correspond to years and months of the files' timestamps. Subdirectories are created if necessary. Destination files are never overwritten;
 * warnings are printed if destination file exists and its timestamp or size is different from the source file's.
 */
public class GroupFilesByPeriod implements Callable<Void> {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GroupFilesByPeriod.class);
    private static final Locale LOCALE = Locale.forLanguageTag("sl-si");

    @Option(names = "-s", required = true, arity = "1..*", description = "Source directories")
    private Path[] sourceDirs;

    @Parameters(index = "0", arity = "1", description = "Destination directory (must exist)")
    private Path destDir;

    @Option(names = "-i", required = true, defaultValue = "glob:{.localized,.DS_Store,*.tmp}",
            description = "Ignored files, in glob or regex syntax (default: ${DEFAULT-VALUE})")
    private String ignore;

    private List<SimpleDateFormat> subdirFormats = Arrays.asList(
            new SimpleDateFormat("yyyy", LOCALE),
            new SimpleDateFormat("MM_MMMM", LOCALE)
    );

    public static void main(String[] args)  {
        CommandLine.call(new GroupFilesByPeriod(), args);
    }

    @Override public Void call() throws Exception {
        PathMatcher ignored = FileSystems.getDefault().getPathMatcher(ignore);
        checkDir(destDir);

        for (Path sourceDir : sourceDirs) {
            log.info("Moving stuff from {}...", sourceDir);
            checkDir(sourceDir);
            long filesAffected = Files.list(sourceDir)
                    .filter(p -> !Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS))
                    .filter(p -> !ignored.matches(p.getFileName()))
                    .filter(p -> !Utils.isHidden(p))
                    .map(this::copyOrWarn)
                    .filter(Boolean::booleanValue)
                    .count();
            log.info("Moved {} files.", filesAffected);
        }

        log.info("Done.");
        return null;
    }

    private boolean copyOrWarn(Path srcFile) {
        Path destSubdir = Paths.get("<unknown>");
        try {
            destSubdir = destDir.resolve(getTimeRelPath(Utils.getLastModifiedTime(srcFile)));
            if (!Files.exists(destSubdir)) {
                log.info("Creating directory {}", destSubdir);
                Files.createDirectories(destSubdir);
            }
            return Utils.copyOrWarn(srcFile, destSubdir, MOVE);
        } catch (IOException e) {
            log.error("Error moving file {} to {}.", srcFile, destSubdir, e);
            return false;
        }
    }

    Path getTimeRelPath(FileTime time) {
        return subdirFormats.stream()
                .reduce(
                        Path.of(""),
                        (p, f) -> p.resolve(f.format(Date.from(time.toInstant()))),
                        Path::resolve
                );
    }

}
