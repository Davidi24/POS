package pos.pos.restaurant.dto;

import java.util.List;

/**
 * One page of the "Choose a branch" cards. Besides the page itself: [allBranches] and [openBranches] count every branch
 * of the restaurant, whatever was searched, so the page can say how many there are and whether any is open.
 */
public record BranchCardPageResponse(
        List<BranchCardResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        long allBranches,
        long openBranches
) {
}
