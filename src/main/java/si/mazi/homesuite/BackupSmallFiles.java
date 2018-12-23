package si.mazi.homesuite;

import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.Callable;

import static java.nio.file.Files.size;
import static si.mazi.homesuite.Utils.Action.COPY;

public class BackupSmallFiles implements Callable<Void> {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BackupSmallFiles.class);

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
        Utils.checkDir(src);
        Utils.checkDir(dest);
        Files.list(src)
                .filter(p -> Files.exists(p, LinkOption.NOFOLLOW_LINKS))
                .filter(p -> !Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS))
                .filter(p -> !ignored.matches(p.getFileName()))
                .forEach(this::backupConditional);
        return null;
    }

    private void backupConditional(Path srcFile) {
        try {
            if (size(srcFile) <= maxSize) {
                Utils.copyOrWarn(srcFile, dest, COPY);
            }
        } catch (IOException e) {
            log.error("Error copying file {} to {}", srcFile, dest, e);
        }
    }

}
