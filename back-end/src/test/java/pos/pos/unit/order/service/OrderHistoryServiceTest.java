package pos.pos.unit.order.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import pos.pos.exception.auth.AuthException;
import pos.pos.order.entity.Order;
import pos.pos.order.enums.OrderStatus;
import pos.pos.order.repository.OrderRepository;
import pos.pos.order.repository.OrderLineItemRepository;
import pos.pos.order.service.*;
import pos.pos.restaurant.service.RestaurantScopeService;
import java.time.OffsetDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class OrderHistoryServiceTest {
    @Mock RestaurantScopeService scope; @Mock OrderRepository repository; @Mock OrderLineItemRepository lineItemRepository; @Mock OrderSupport support; @Mock Authentication auth;
    @InjectMocks OrderHistoryService service;
    UUID r=UUID.randomUUID(), b=UUID.randomUUID();
    @Test void rejectsInvalidPaginationRangeStatusAndSearch() {
        var now=OffsetDateTime.now();
        assertThatThrownBy(()->service.history(auth,r,b,null,null,null,null,null,null,-1,40)).isInstanceOf(AuthException.class);
        assertThatThrownBy(()->service.history(auth,r,b,null,null,null,null,null,null,0,101)).isInstanceOf(AuthException.class);
        assertThatThrownBy(()->service.history(auth,r,b,now,now,null,null,null,null,0,40)).isInstanceOf(AuthException.class);
        assertThatThrownBy(()->service.history(auth,r,b,null,null,OrderStatus.OPEN,null,null,null,0,40)).isInstanceOf(AuthException.class);
        assertThatThrownBy(()->service.history(auth,r,b,null,null,null,null,null,"a".repeat(201),0,40)).isInstanceOf(AuthException.class);
        verifyNoInteractions(repository);
    }
    @Test void branchDenialPreventsDataRead() {
        doThrow(new AuthException("Denied",org.springframework.http.HttpStatus.FORBIDDEN)).when(scope).requireAccessibleBranch(auth,r,b);
        assertThatThrownBy(()->service.history(auth,r,b,null,null,null,null,null,null,0,40)).isInstanceOf(AuthException.class);
        verifyNoInteractions(repository,support);
    }
    @Test void paginationIsDatabaseBoundedWithStableTieBreak() {
        when(repository.findAll(ArgumentMatchers.<Specification<Order>>any(),any(Pageable.class))).thenReturn(new PageImpl<>(List.of(),PageRequest.of(2,40),81));
        var result=service.history(auth,r,b,null,null,OrderStatus.CLOSED,null,null,null,2,40);
        assertThat(result.page()).isEqualTo(2); assertThat(result.totalElements()).isEqualTo(81); assertThat(result.hasNext()).isFalse();
        var page=ArgumentCaptor.forClass(Pageable.class); verify(repository).findAll(ArgumentMatchers.<Specification<Order>>any(),page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(40);
        assertThat(page.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC,"openedAt","id"));
    }
}
