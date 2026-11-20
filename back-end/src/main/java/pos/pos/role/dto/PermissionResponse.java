package pos.pos.role.dto;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionResponse {

    private UUID id;
    private String code;
    private String name;
    private String description;
    // The permission this one needs (a workspace's "Open …", or a part's "View …"); null for a workspace's "Open …".
    private String parentCode;
    // Owner-level: giving it asks to be careful first.
    private boolean careful;
    // Where the app shows it (PermissionTree.Place): the workspace (POS, KDS, INVENTORY, ADMIN, STATISTICS, FRAUD, PLATFORM),
    // the place in its bar (Tables, Orders…; null for the workspace's "Open …") and the order to show it in.
    private String workspace;
    private String part;
    private Integer position;
    // The Super Admin's own: not shown inside a restaurant.
    private boolean platformOnly;
    // Comes with another permission (bundledInto) and is never shown on its own.
    private String bundledInto;
    // FLOORS or ROLES when it can be given for some of them only.
    private String scope;

}
