package pos.pos.unit.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import pos.pos.exception.auth.AuthException;
import pos.pos.utils.PageableUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageableUtilsTest {

    @Test
    @DisplayName("create() should apply defaults and trim the sort direction")
    void shouldApplyDefaultsAndTrimSortDirection() {
        Pageable pageable = PageableUtils.create(null, null, " asc ", "createdAt", 20);

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection().name()).isEqualTo("ASC");
    }

    @Test
    @DisplayName("resolveDirection() should reject unsupported values")
    void shouldRejectUnsupportedDirection() {
        assertThatThrownBy(() -> PageableUtils.resolveDirection("sideways"))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid sort direction");
    }
}
