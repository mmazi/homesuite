package si.mazi.homesuite;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class GroupFilesByPeriodTest {
    private static final Logger log = LoggerFactory.getLogger(GroupFilesByPeriodTest.class);

    @Test
    void shouldCreateDatePath() {
        FileTime jul23 = FileTime.from(LocalDate.of(2018, 8, 23).atStartOfDay(ZoneId.of("Europe/Ljubljana")).toInstant());
        Path subdir = new GroupFilesByPeriod().getTimeRelPath(jul23);
        assertThat(subdir).isEqualByComparingTo(Path.of("2018", "08_avgust"));
    }
}