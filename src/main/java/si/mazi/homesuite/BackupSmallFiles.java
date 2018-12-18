package si.mazi.homesuite;

import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.temporal.ChronoField;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.Callable;

import static java.nio.file.Files.size;

public class BackupSmallFiles implements Callable<Void> {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BackupSmallFiles.class);

    private static final Map<String, ExFunction<Path, Object>> WARN_DIFF_PROPS = Map.of(
            "modified time", BackupSmallFiles::lastModifiedSec,
            "size", Files::size
    );

    @Parameters(index = "0", arity = "1", description = "The source directory (must exist; ~ syntax not supported)")
    private Path src;

    @Parameters(index = "1", arity = "1", description = "The destination directory (must exist)")
    private Path dest;

    @Option(names = "-s", required = true, defaultValue = "100000000",
            description = "The maximum size of files to copy in bytes (larger files will be ignored) (default: ${DEFAULT-VALUE})")
    private Long maxSize;

    @Option(names = "-i", required = true, defaultValue = "glob:{*.dmg,*.pkg,*.exe,*.qpkg,*.tar.bz2,*.tar.gz,.localized,.DS_Store}",
            description = "Ignored files, in glob or regex syntax (default: ${DEFAULT-VALUE})")
    private String ignore;

    public static void main(String[] args)  {
        CommandLine.call(new BackupSmallFiles(), args);
    }

    @Override public Void call() throws Exception {
        PathMatcher ignored = FileSystems.getDefault().getPathMatcher(ignore);
        checkDir(src);
        checkDir(dest);
        Files.list(src)
                .filter(p -> Files.exists(p, LinkOption.NOFOLLOW_LINKS))
                .filter(p -> !Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS))
                .filter(p -> !ignored.matches(p.getFileName()))
                .forEach(this::backupConditional);
        return null;
    }

    private void checkDir(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("Directory does not exist: " + path);
        } else if (!Files.isDirectory(path)) {
            throw new IOException("This is not a directory: " + path);
        }
    }

    private void backupConditional(Path srcFile) {
        try {
            long srcFileSize = size(srcFile);
            if (srcFileSize <= maxSize) {
                Path fileName = srcFile.getFileName();
                Path destFile = dest.resolve(fileName);
                if (Files.exists(destFile)) {
                    StringJoiner joiner = new StringJoiner(", ");
                    for (String propName : WARN_DIFF_PROPS.keySet()) {
                        ExFunction<Path, Object> propertyFn = WARN_DIFF_PROPS.get(propName);
                        Object srcValue = propertyFn.apply(srcFile);
                        Object destValue = propertyFn.apply(destFile);
                        if (!srcValue.equals(destValue)) {
                            String format = String.format("%s: %s <> %s", propName, srcValue, destValue);
                            joiner.add(format);
                        }
                    }
                    String diffs = joiner.toString();
                    if (diffs.isEmpty()) {
                        log.trace("{} already exists.", destFile);
                    } else {
                        log.info("Files differ in {}: {}", diffs, fileName);
                    }
                } else {
                    log.debug("Copying file {} to {} ({} b)", srcFile, destFile, srcFileSize);
                    Files.copy(srcFile, destFile);
                    Files.setLastModifiedTime(destFile, Files.getLastModifiedTime(srcFile));
                }
            }
        } catch (IOException e) {
            log.error("Error copying file {} to {}", srcFile, dest, e);
        }
    }

    private static Instant lastModifiedSec(Path path) throws IOException {
        return Files.getLastModifiedTime(path).toInstant().with(ChronoField.MILLI_OF_SECOND, 0);
    }

    @FunctionalInterface
    interface ExFunction<A, B> {
        B apply(A a) throws IOException;
    }
}
