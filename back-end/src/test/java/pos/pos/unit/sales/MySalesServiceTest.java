package pos.pos.unit.sales;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import pos.pos.exception.auth.AuthException;
import pos.pos.restaurant.entity.*;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.sales.*;
import pos.pos.shift.entity.Shift;
import pos.pos.shift.repository.ShiftRepository;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class MySalesServiceTest {
    @Mock RestaurantScopeService scope; @Mock ShiftRepository shifts; @Mock UserRepository users; @Mock MySalesRepository reports;
    @InjectMocks MySalesService service;
    UUID r=UUID.randomUUID(), b=UUID.randomUUID(), u=UUID.randomUUID();
    Authentication auth=authentication("ORDER_READ");
    static Authentication authentication(String... p) { return new UsernamePasswordAuthenticationToken("actor","",Arrays.stream(p).map(SimpleGrantedAuthority::new).toList()); }
    void setup() {
        Restaurant restaurant=new Restaurant();restaurant.setId(r);restaurant.setTimezone("Europe/Berlin");restaurant.setCurrency("EUR");
        Branch branch=new Branch();branch.setId(b);branch.setRestaurant(restaurant);
        User user=new User();user.setId(u);user.setRestaurantId(r);user.setDefaultBranchId(b);user.setFirstName("Test");user.setLastName("Waiter");
        when(scope.requireAccessibleBranch(auth,r,b)).thenReturn(branch);when(scope.currentUserId(auth)).thenReturn(u);
        lenient().when(scope.currentActor(auth)).thenReturn(user);
        lenient().when(shifts.inWindow(eq(r),eq(b),eq(u),any(),any(),any(Pageable.class))).thenReturn(List.of());
    }
    @Test void orderPermissionRequired() {
        assertThatThrownBy(()->service.report(authentication(),r,b,null,null,null)).isInstanceOf(AuthException.class);
        verifyNoInteractions(reports,scope);
    }
    @Test void staffCannotRequestSomeoneElse() {
        setup();assertThatThrownBy(()->service.report(auth,r,b,null,UUID.randomUUID(),null)).isInstanceOf(AuthException.class);
        verifyNoInteractions(reports,users);
    }
    @Test void dateUsesRestaurantTimezoneIncludingDaylightSaving() {
        setup();var result=service.report(auth,r,b,LocalDate.of(2026,3,29),null,null);
        assertThat(Duration.between(result.from(),result.to()).toHours()).isEqualTo(23);
        assertThat(result.from()).isEqualTo(OffsetDateTime.parse("2026-03-29T00:00:00+01:00"));
        verify(reports).totals(r,b,u,result.from(),result.to(),null,"Europe/Berlin","EUR");
    }
    @Test void shiftMustBelongToSelectedStaffAndBranch() {
        setup();Shift shift=new Shift();shift.setId(UUID.randomUUID());
        Restaurant rr=new Restaurant();rr.setId(r);Branch bb=new Branch();bb.setId(b);User other=new User();other.setId(UUID.randomUUID());
        shift.setRestaurant(rr);shift.setBranch(bb);shift.setUser(other);when(shifts.findById(shift.getId())).thenReturn(Optional.of(shift));
        assertThatThrownBy(()->service.report(auth,r,b,null,null,shift.getId())).isInstanceOf(AuthException.class);verifyNoInteractions(reports);
    }
    @Test void overnightShiftUsesWholeActualWindow() {
        setup();Shift shift=new Shift();shift.setId(UUID.randomUUID());Restaurant rr=new Restaurant();rr.setId(r);Branch bb=new Branch();bb.setId(b);User staff=new User();staff.setId(u);
        shift.setRestaurant(rr);shift.setBranch(bb);shift.setUser(staff);shift.setStartedAt(OffsetDateTime.parse("2026-09-25T21:00:00Z"));shift.setEndedAt(OffsetDateTime.parse("2026-09-26T02:00:00Z"));
        when(shifts.findById(shift.getId())).thenReturn(Optional.of(shift));
        var result=service.report(auth,r,b,LocalDate.of(2026,9,25),null,shift.getId());
        assertThat(result.from()).isEqualTo(shift.getStartedAt());assertThat(result.to()).isEqualTo(shift.getEndedAt());
        verify(reports).totals(r,b,u,result.from(),result.to(),shift.getId(),"Europe/Berlin","EUR");
    }
    @Test void shiftOutsideSelectedDateIsRejected() {
        setup();Shift shift=new Shift();shift.setId(UUID.randomUUID());Restaurant rr=new Restaurant();rr.setId(r);Branch bb=new Branch();bb.setId(b);User staff=new User();staff.setId(u);
        shift.setRestaurant(rr);shift.setBranch(bb);shift.setUser(staff);shift.setStartedAt(OffsetDateTime.parse("2026-09-25T21:00:00Z"));shift.setEndedAt(OffsetDateTime.parse("2026-09-26T02:00:00Z"));
        when(shifts.findById(shift.getId())).thenReturn(Optional.of(shift));

        assertThatThrownBy(()->service.report(auth,r,b,LocalDate.of(2026,9,27),null,shift.getId()))
                .isInstanceOf(AuthException.class).hasMessageContaining("does not overlap the selected date");
        verifyNoInteractions(reports);
    }
}
