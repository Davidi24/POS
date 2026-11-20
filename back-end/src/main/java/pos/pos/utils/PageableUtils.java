package pos.pos.utils;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public final class PageableUtils {

    private PageableUtils() {
    }

    // The biggest page anyone gets; asking for more returns this many.
    public static final int MAX_PAGE_SIZE = 200;

    public static Pageable create(
            Integer page,
            Integer size,
            String direction,
            String sortProperty,
            int defaultPageSize
    ) {
        return of(page, size, defaultPageSize, Sort.by(resolveDirection(direction), sortProperty));
    }

    /** A page request from user input: a negative page or a size below 1 is refused, a huge size is capped. */
    public static PageRequest of(Integer page, Integer size, int defaultPageSize, Sort sort) {
        int resolvedPage = page == null ? 0 : page;
        int resolvedSize = size == null ? defaultPageSize : size;
        if (resolvedPage < 0) {
            throw new AuthException("page must not be negative", HttpStatus.BAD_REQUEST);
        }
        if (resolvedSize < 1) {
            throw new AuthException("size must be at least 1", HttpStatus.BAD_REQUEST);
        }
        return PageRequest.of(resolvedPage, Math.min(resolvedSize, MAX_PAGE_SIZE), sort);
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
