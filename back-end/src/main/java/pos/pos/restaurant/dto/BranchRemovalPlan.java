package pos.pos.restaurant.dto;

import java.util.List;

public record BranchRemovalPlan(boolean canRemove, List<String> reasons,
        long staff, long protectedStaff, long devices, long tables, long kitchenStations, long stockLocations,
        boolean canDeactivateStaff, long expectedVersion, String previewToken) {}
