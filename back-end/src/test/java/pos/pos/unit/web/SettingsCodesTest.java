package pos.pos.unit.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pos.pos.settings.mapper.SettingsCodes;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SettingsCodes")
class SettingsCodesTest {

    @Test
    @DisplayName("tip suggestions round-trip, skipping blanks and junk")
    void percents() {
        assertThat(SettingsCodes.parsePercents("5,10,15")).containsExactly(5, 10, 15);
        assertThat(SettingsCodes.parsePercents(" 5 , ,x, 20 ")).containsExactly(5, 20);
        assertThat(SettingsCodes.parsePercents(null)).isEmpty();
        assertThat(SettingsCodes.parsePercents("")).isEmpty();
        assertThat(SettingsCodes.joinPercents(List.of(10, 5, 10))).isEqualTo("10,5");
        assertThat(SettingsCodes.joinPercents(null)).isEmpty();
    }

    @Test
    @DisplayName("rule codes are upper-cased, trimmed, de-duplicated and sorted")
    void codes() {
        Set<String> codes = new LinkedHashSet<>(List.of("high_tip", " LARGE_REFUND ", "HIGH_TIP"));
        assertThat(SettingsCodes.joinCodes(codes)).isEqualTo("HIGH_TIP,LARGE_REFUND");
        assertThat(SettingsCodes.parseCodes("HIGH_TIP, large_refund,,HIGH_TIP")).containsExactly("HIGH_TIP", "LARGE_REFUND");
        assertThat(SettingsCodes.joinCodes(Set.of())).isEmpty();
        assertThat(SettingsCodes.parseCodes(null)).isEmpty();
    }
}
