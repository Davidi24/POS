package pos.pos.unit.kds.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import pos.pos.exception.auth.AuthException;
import pos.pos.kds.entity.KdsStation;
import pos.pos.kds.entity.KdsTicket;
import pos.pos.kds.enums.KdsTicketStatus;
import pos.pos.kds.mapper.KdsMapper;
import pos.pos.kds.repository.KdsTicketRepository;
import pos.pos.kds.service.*;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.service.RestaurantScopeService;
import java.time.OffsetDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KdsHistoryServiceTest {
    @Mock RestaurantScopeService scope;
    @Mock KdsSupport support;
    @Mock KdsTicketRepository repository;
    @Mock KdsMapper mapper;
    @Mock Authentication authentication;
    @InjectMocks KdsHistoryService service;
    UUID restaurant = UUID.randomUUID(), branch = UUID.randomUUID();
    @Test void rejectsUnboundedPagesAndInvalidDatesOrStatuses() {
        var now = OffsetDateTime.now();
        assertThatThrownBy(() -> service.history(authentication, restaurant, branch, null, null, null, null, null, -1, 30)).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.history(authentication, restaurant, branch, null, null, null, null, null, 0, 101)).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.history(authentication, restaurant, branch, null, null, now, now, null, 0, 30)).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.history(authentication, restaurant, branch, null, null, null, null, KdsTicketStatus.READY, 0, 30)).isInstanceOf(AuthException.class);
        verifyNoInteractions(repository);
    }
    @Test void scopeDenialPreventsAnyRepositoryRead() {
        doThrow(new AuthException("Denied", org.springframework.http.HttpStatus.FORBIDDEN)).when(scope).requireAccessibleBranch(authentication, restaurant, branch);
        assertThatThrownBy(() -> service.history(authentication, restaurant, branch, null, null, null, null, null, 0, 30)).isInstanceOf(AuthException.class);
        verifyNoInteractions(repository, support, mapper);
    }
    @Test void returnsBoundedHistoryIncludingPaginationMetadata() {
        UUID stationId = UUID.randomUUID();
        when(repository.findAll(ArgumentMatchers.<Specification<KdsTicket>>any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 30), 61));
        when(mapper.mapTicketResponses(List.of())).thenReturn(List.of());
        var result = service.history(authentication, restaurant, branch, stationId, null, null, null, KdsTicketStatus.CANCELLED, 2, 30);
        assertThat(result.page()).isEqualTo(2); assertThat(result.totalElements()).isEqualTo(61); assertThat(result.hasNext()).isFalse();
        verify(scope).requireAccessibleBranch(authentication, restaurant, branch);
        verify(support).requireStationInBranch(branch, stationId);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(ArgumentMatchers.<Specification<KdsTicket>>any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(30);
        assertThat(pageable.getValue().getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
    }
    @Test void rejectsDevicesFromAnotherBranchAndMismatchedStations() {
        UUID deviceId = UUID.randomUUID();
        var station = new KdsStation(); station.setId(UUID.randomUUID());
        var otherBranch = new Branch(); otherBranch.setId(UUID.randomUUID()); station.setBranch(otherBranch);
        when(support.requireStationForDevice(restaurant, deviceId)).thenReturn(station);
        assertThatThrownBy(() -> service.history(authentication, restaurant, branch, null, deviceId, null, null, null, 0, 30)).isInstanceOf(AuthException.class);
        otherBranch.setId(branch);
        assertThatThrownBy(() -> service.history(authentication, restaurant, branch, UUID.randomUUID(), deviceId, null, null, null, 0, 30)).isInstanceOf(AuthException.class);
        verifyNoInteractions(repository);
    }
}
