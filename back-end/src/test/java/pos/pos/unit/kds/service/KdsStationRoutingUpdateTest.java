package pos.pos.unit.kds.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import pos.pos.kds.dto.KdsStationRoutingRequest;
import pos.pos.kds.dto.UpsertKdsStationRequest;
import pos.pos.kds.entity.KdsStation;
import pos.pos.kds.entity.KdsStationRouting;
import pos.pos.kds.enums.KdsStationType;
import pos.pos.kds.mapper.KdsMapper;
import pos.pos.kds.service.KdsStationCommandService;
import pos.pos.kds.service.KdsSupport;
import pos.pos.menu.entity.MenuItem;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.settings.service.SettingsAuditService;
import pos.pos.settings.service.SettingsDomainSupport;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KdsStationRoutingUpdateTest {

    private static final UUID RESTAURANT_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final UUID STATION_ID = UUID.randomUUID();

    @Mock SettingsDomainSupport settingsDomainSupport;
    @Mock SettingsAuditService settingsAuditService;
    @Mock KdsSupport kdsSupport;
    @Mock Authentication authentication;
    @InjectMocks KdsStationCommandService service;

    // Regression: saving a station with its dishes removed and re-added each routing, and the database rejected
    // the duplicate (station, dish) row. Dishes already on the station must be updated in place.
    @Test void savingAStationKeepsItsExistingDishRowsAndOnlyChangesWhatDiffers() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        Branch branch = new Branch();
        branch.setId(BRANCH_ID);
        branch.setRestaurant(restaurant);
        MenuItem fries = dish();
        MenuItem burger = dish();
        MenuItem salad = dish();
        KdsStation station = new KdsStation();
        station.setId(STATION_ID);
        KdsStationRouting friesRouting = routing(fries);
        KdsStationRouting burgerRouting = routing(burger);
        station.addRouting(friesRouting);
        station.addRouting(burgerRouting);

        when(settingsDomainSupport.requireManageableBranch(authentication, RESTAURANT_ID, BRANCH_ID)).thenReturn(branch);
        when(kdsSupport.requireStationInBranch(BRANCH_ID, STATION_ID)).thenReturn(station);
        when(kdsSupport.requireMenuItemInRestaurant(RESTAURANT_ID, salad.getId())).thenReturn(salad);
        when(kdsSupport.saveStation(station)).thenReturn(station);
        when(kdsSupport.mapper()).thenReturn(mock(KdsMapper.class));

        service.updateStation(authentication, RESTAURANT_ID, BRANCH_ID, STATION_ID, UpsertKdsStationRequest.builder()
                .name("Grill")
                .stationType(KdsStationType.GRILL)
                .active(false)
                .routings(List.of(
                        KdsStationRoutingRequest.builder().menuItemId(fries.getId()).active(false).build(),
                        KdsStationRoutingRequest.builder().menuItemId(salad.getId()).build()
                ))
                .build());

        assertThat(station.getRoutings()).hasSize(2);
        assertThat(station.getRoutings()).contains(friesRouting).doesNotContain(burgerRouting);
        assertThat(friesRouting.isActive()).isFalse();
        assertThat(station.getRoutings()).anyMatch(routing -> routing.getMenuItem() == salad);
    }

    private static MenuItem dish() {
        MenuItem item = new MenuItem();
        item.setId(UUID.randomUUID());
        return item;
    }

    private static KdsStationRouting routing(MenuItem item) {
        KdsStationRouting routing = new KdsStationRouting();
        routing.setMenuItem(item);
        routing.setActive(true);
        return routing;
    }
}
