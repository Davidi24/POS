package pos.pos.tables.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

// Tables leaving a merged group and becoming free, and the table that keeps the guests and the order (user, 2026-11-01).
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UnmergeTablesRequest {

    @NotEmpty(message = "Choose the tables to unmerge")
    @Size(max = 50, message = "At most 50 tables")
    private List<UUID> freeTableIds;

    // Left out: the main table if it stays, else the biggest one left.
    private UUID keepTableId;
}
