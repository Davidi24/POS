package pos.pos.utils;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public final class PageableUtils {

    private PageableUtils() {
    }

    public static Pageable create(
            Integer page,
            Integer size,
            String direction,
            String sortProperty,
            int defaultPageSize
    ) {
        return PageRequest.of(
                page == null ? 0 : page,
                size == null ? defaultPageSize : size,
                Sort.by(resolveDirection(direction), sortProperty)
        );
    }

    public static Sort.Direction resolveDirection(String direction) {
        try {
            String normalizedDirection = NormalizationUtils.normalize(direction);
            return Sort.Direction.fromString(normalizedDirection == null ? "desc" : normalizedDirection);
        } catch (IllegalArgumentException ex) {
            throw new AuthException("Invalid sort direction", HttpStatus.BAD_REQUEST);
        }
    }
}
