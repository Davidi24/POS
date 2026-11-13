package pos.pos.restaurant.dto;

import java.util.List;

/**
 * Whether a branch can be removed now and, if not, what still uses it. {@code reasons} are ready to show
 * ("2 open orders", "5 staff work here").
 */
public record BranchRemovalCheckResponse(
        boolean canRemove,
        long openOrders,
        long upcomingBookings,
        long openShifts,
        long staff,
        long devices,
        boolean lastBranch,
        List<String> reasons,
        BranchRemovalPlan archive
) {
    public BranchRemovalCheckResponse(boolean canRemove, long openOrders, long upcomingBookings, long openShifts,
            long staff, long devices, boolean lastBranch, List<String> reasons) {
        this(canRemove, openOrders, upcomingBookings, openShifts, staff, devices, lastBranch, reasons, null);
    }
    public BranchRemovalCheckResponse withArchive(BranchRemovalPlan plan) {
        return new BranchRemovalCheckResponse(canRemove, openOrders, upcomingBookings, openShifts, staff, devices, lastBranch, reasons, plan);
    }
}
