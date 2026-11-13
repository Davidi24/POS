package pos.pos.unit.restaurant.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.restaurant.RestaurantAccessNotAllowedException;
import pos.pos.restaurant.dto.BranchCardPageResponse;
import pos.pos.restaurant.dto.BranchCardResponse;
import pos.pos.restaurant.enums.BranchStatus;
import pos.pos.restaurant.repository.RestaurantCardRepository;
import pos.pos.restaurant.service.RestaurantPlatformService;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RestaurantPlatformService branch cards")
class RestaurantPlatformServiceBranchCardsTest {

    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");

    @Mock
    private RestaurantScopeService restaurantScopeService;

    @Mock
    private RestaurantCardRepository restaurantCardRepository;

    @InjectMocks
    private RestaurantPlatformService service;

    private final Authentication authentication = new UsernamePasswordAuthenticationToken("admin", null, List.of());

    @Test
    @DisplayName("a middle page asks for its slice and says there is more; the totals count every branch")
    void shouldPageAndSearch() {
        given(restaurantCardRepository.branchCards(RESTAURANT_ID, "%north%", 10, 10)).willReturn(cards(10));
        given(restaurantCardRepository.countBranchCards(RESTAURANT_ID, "%north%")).willReturn(25L);
        given(restaurantCardRepository.countBranchCards(RESTAURANT_ID, null)).willReturn(140L);
        given(restaurantCardRepository.countOpenBranches(RESTAURANT_ID)).willReturn(120L);

        BranchCardPageResponse page = service.branchCards(authentication, RESTAURANT_ID, " North ", 1, 10);

        assertThat(page.items()).hasSize(10);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.totalElements()).isEqualTo(25);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.allBranches()).isEqualTo(140);
        assertThat(page.openBranches()).isEqualTo(120);
        verify(restaurantScopeService).requireAccessibleRestaurant(authentication, RESTAURANT_ID);
    }

    @Test
    @DisplayName("the last page has no next page, and without a search the matching count is every branch")
    void shouldEndOnLastPage() {
        given(restaurantCardRepository.branchCards(RESTAURANT_ID, null, 24, 24)).willReturn(cards(6));
        given(restaurantCardRepository.countBranchCards(RESTAURANT_ID, null)).willReturn(30L);
        given(restaurantCardRepository.countOpenBranches(RESTAURANT_ID)).willReturn(0L);

        BranchCardPageResponse page = service.branchCards(authentication, RESTAURANT_ID, "  ", 1, 24);

        assertThat(page.hasNext()).isFalse();
        assertThat(page.allBranches()).isEqualTo(30);
        assertThat(page.openBranches()).isZero();
    }

    @Test
    @DisplayName("sizes are kept between 1 and 100")
    void shouldClampSize() {
        given(restaurantCardRepository.branchCards(RESTAURANT_ID, null, 100, 0)).willReturn(cards(0));

        service.branchCards(authentication, RESTAURANT_ID, null, 0, 5_000);

        verify(restaurantCardRepository).branchCards(RESTAURANT_ID, null, 100, 0);
    }

    @Test
    @DisplayName("a page beyond what the database can address is a 400, and someone without access learns nothing")
    void shouldGuardPagesAndAccess() {
        assertThatThrownBy(() -> service.branchCards(authentication, RESTAURANT_ID, null, Integer.MAX_VALUE, 100))
                .isInstanceOfSatisfying(AuthException.class, error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        given(restaurantScopeService.requireAccessibleRestaurant(authentication, RESTAURANT_ID))
                .willThrow(new RestaurantAccessNotAllowedException());
        assertThatThrownBy(() -> service.branchCards(authentication, RESTAURANT_ID, null, 0, 24))
                .isInstanceOf(RestaurantAccessNotAllowedException.class);
        verify(restaurantCardRepository, never()).branchCards(any(), any(), anyInt(), anyInt());
    }

    private static List<BranchCardResponse> cards(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new BranchCardResponse(UUID.randomUUID(), "Branch " + i, "B" + i, BranchStatus.ACTIVE, true,
                        null, null, null, null, "Tirana", "AL", 0, 0))
                .toList();
    }
}
