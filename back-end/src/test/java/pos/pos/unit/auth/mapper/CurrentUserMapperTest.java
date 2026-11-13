package pos.pos.unit.auth.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pos.pos.auth.dto.CurrentUserResponse;
import pos.pos.auth.mapper.CurrentUserMapper;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.user.entity.User;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CurrentUserMapper")
class CurrentUserMapperTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000021");

    private final pos.pos.restaurant.repository.BranchRepository branches = org.mockito.Mockito.mock(pos.pos.restaurant.repository.BranchRepository.class);
    private final CurrentUserMapper currentUserMapper = new CurrentUserMapper(branches);

    @Test
    @DisplayName("Should map entity fields and normalize duplicated auth codes")
    void shouldMapEntityFieldsAndNormalizeDuplicatedAuthCodes() {
        User user = User.builder()
                .id(USER_ID)
                .email("owner@pos.local")
                .username("owner.main")
                .firstName("Olivia")
                .lastName("Owner")
                .phone("+49-555-0202")
                .isActive(true)
                .emailVerified(true)
                .phoneVerified(false)
                .build();

        CurrentUserResponse response = currentUserMapper.toCurrentUserResponse(
                user,
                List.of("OWNER", "OWNER", "MANAGER"),
                List.of("USERS_CREATE", "USERS_CREATE", "SESSIONS_MANAGE")
        );

        assertThat(response.getId()).isEqualTo(USER_ID);
        assertThat(response.getIsActive()).isTrue();
        assertThat(response.getRoles()).containsExactly("OWNER", "MANAGER");
        assertThat(response.getPermissions()).containsExactly("USERS_CREATE", "SESSIONS_MANAGE");
    }

    @Test
    @DisplayName("Should map authenticated principal fields and default missing code lists to empty")
    void shouldMapPrincipalFieldsAndDefaultMissingCodeListsToEmpty() {
        AuthenticatedUser user = AuthenticatedUser.builder()
                .id(USER_ID)
                .email("manager@pos.local")
                .username("manager.main")
                .firstName("Maria")
                .lastName("Manager")
                .phone("+49-555-0101")
                .active(true)
                .emailVerified(true)
                .phoneVerified(true)
                .build();

        CurrentUserResponse response = currentUserMapper.toCurrentUserResponse(user, null, null);

        assertThat(response.getId()).isEqualTo(USER_ID);
        assertThat(response.getEmail()).isEqualTo("manager@pos.local");
        assertThat(response.getIsActive()).isTrue();
        assertThat(response.getRoles()).isEmpty();
        assertThat(response.getPermissions()).isEmpty();
    }
    @Test void onlyAnOpenBranchInTheSameRestaurantIsRestored() {
        UUID restaurantId=UUID.randomUUID(), branchId=UUID.randomUUID();
        User user=User.builder().id(USER_ID).restaurantId(restaurantId).defaultBranchId(branchId).build();
        var branch=new pos.pos.restaurant.entity.Branch();branch.setId(branchId);branch.setActive(true);branch.setStatus(pos.pos.restaurant.enums.BranchStatus.ACTIVE);
        org.mockito.Mockito.when(branches.findByIdAndRestaurantIdAndDeletedAtIsNull(branchId,restaurantId)).thenReturn(java.util.Optional.of(branch));
        assertThat(currentUserMapper.toCurrentUserResponse(user,List.of(),List.of()).getDefaultBranchId()).isEqualTo(branchId);
        for(var status:List.of(pos.pos.restaurant.enums.BranchStatus.INACTIVE,pos.pos.restaurant.enums.BranchStatus.TEMPORARILY_CLOSED,pos.pos.restaurant.enums.BranchStatus.ARCHIVED)) {
            branch.setStatus(status);
            assertThat(currentUserMapper.toCurrentUserResponse(user,List.of(),List.of()).getDefaultBranchId()).isNull();
        }
        branch.setStatus(pos.pos.restaurant.enums.BranchStatus.ACTIVE);branch.setActive(false);
        assertThat(currentUserMapper.toCurrentUserResponse(user,List.of(),List.of()).getDefaultBranchId()).isNull();
        org.mockito.Mockito.when(branches.findByIdAndRestaurantIdAndDeletedAtIsNull(branchId,restaurantId)).thenReturn(java.util.Optional.empty());
        assertThat(currentUserMapper.toCurrentUserResponse(user,List.of(),List.of()).getDefaultBranchId()).isNull();
    }

}
