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

    @Test
    @DisplayName("of() refuses a negative page or a size below 1 and caps huge sizes")
    void shouldGuardPageInput() {
        assertThatThrownBy(() -> PageableUtils.of(-1, 10, 20, org.springframework.data.domain.Sort.unsorted()))
                .isInstanceOf(AuthException.class)
                .hasMessage("page must not be negative");
        assertThatThrownBy(() -> PageableUtils.of(0, 0, 20, org.springframework.data.domain.Sort.unsorted()))
                .isInstanceOf(AuthException.class)
                .hasMessage("size must be at least 1");
        assertThatThrownBy(() -> PageableUtils.create(Integer.MIN_VALUE, null, "asc", "createdAt", 20))
                .isInstanceOf(AuthException.class);
        assertThat(PageableUtils.of(3, Integer.MAX_VALUE, 20, org.springframework.data.domain.Sort.unsorted()).getPageSize())
                .isEqualTo(PageableUtils.MAX_PAGE_SIZE);
        assertThat(PageableUtils.of(null, null, 25, org.springframework.data.domain.Sort.unsorted()).getPageSize()).isEqualTo(25);
    }
}
