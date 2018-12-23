package si.mazi.homesuite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoField;
import java.util.Map;
import java.util.StringJoiner;

import static java.nio.file.Files.size;

enum Utils {
    ;

    private static final Logger log = LoggerFactory.getLogger(Utils.class);

    private static final Map<String, ExFunction<Path, Object>> WARN_DIFF_PROPS = Map.of(
            "modified time", Utils::lastModifiedSec,
            "size", Files::size
    );

    private static Instant lastModifiedSec(Path path) throws IOException {
        return Files.getLastModifiedTime(path).toInstant().with(ChronoField.MILLI_OF_SECOND, 0);
    }

    static boolean copyOrWarn(Path srcFile, Path destDir, Action action) throws IOException {
        Path fileName = srcFile.getFileName();
        Path destFile = destDir.resolve(fileName);
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
            return false;
        } else {
            log.debug("{} file {} to {} ({} b)", action.actionName, srcFile, destFile, size(srcFile));
            FileTime lastModifiedTime = Files.getLastModifiedTime(srcFile);
            action.action.invoke(srcFile, destFile);
            if (Files.exists(destFile)) {
                Files.setLastModifiedTime(destFile, lastModifiedTime);
            }
            return true;
        }
    }

    static void checkDir(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("Directory does not exist: " + path);
        } else if (!Files.isDirectory(path)) {
            throw new IOException("This is not a directory: " + path);
        }
    }

    static FileTime getLastModifiedTime(Path srcFile) {
        try {
            return Files.getLastModifiedTime(srcFile);
        } catch (IOException e) {
            throw new RuntimeException("Error getting last modified time for " + srcFile);
        }
    }

    static boolean isHidden(Path p) {
        try {
            return Files.isHidden(p);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    interface ExFunction<A, B> {
        B apply(A a) throws IOException;
    }

    @FunctionalInterface
    interface FileAction {
        void invoke(Path source, Path target) throws IOException;
    }

    public enum Action {
        COPY("Copying", Files::copy),
        MOVE("Moving", Files::move),
        DUMMY("Visiting", (s, t) -> {/* do nothing*/}),

        ;
        private String actionName;
        private FileAction action;

        Action(String actionName, FileAction action) {
            this.actionName = actionName;
            this.action = action;
        }

    }
}
