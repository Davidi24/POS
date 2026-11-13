package pos.pos.restaurant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.restaurant.dto.RestaurantResponse;
import pos.pos.restaurant.dto.TimezoneChangeDtos;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.mapper.RestaurantMapper;
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.restaurant.repository.RestaurantUsageRepository;
import pos.pos.restaurant.util.RestaurantFieldNormalizer;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Moves a restaurant to another time zone. Orders, payments and shifts are fixed moments, so their clock times read
 * differently afterwards; opening hours and menu hours are clock times and stay as they are. Bookings still to come
 * either keep the clock time the guest was told (the default) or keep their moment.
 */
@Service
@RequiredArgsConstructor
public class RestaurantTimezoneService {

    private static final String ONLY_SUPER_ADMIN = "Only the Super Admin can change the time zone";
    private static final DateTimeFormatter EXAMPLE = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.ENGLISH);
    private static final String LIVE_BOOKING = "status in ('PENDING','CONFIRMED','CHECKED_IN','SEATED')";

    private final RestaurantScopeService restaurantScopeService;
    private final RestaurantUsageRepository restaurantUsageRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantMapper restaurantMapper;
    private final NamedParameterJdbcTemplate jdbc;
    private final RestaurantChangeGuard changes;
    private final TimezoneBookingGuard bookingGuard;
    private final pos.pos.reservation.repository.ReservationRepository bookings;
    private final pos.pos.reservation.service.ReservationTableAssignmentService tables;
    private final jakarta.persistence.EntityManager entities;

    @Value("${spring.jpa.properties.hibernate.default_schema:public}")
    private String schemaName;

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public TimezoneChangeDtos.Preview preview(Authentication authentication, UUID restaurantId, TimezoneChangeDtos.PreviewRequest request) {
        Restaurant restaurant = restaurantScopeService.requirePlatformManagedRestaurant(authentication, restaurantId, ONLY_SUPER_ADMIN);
        ZoneId newZone = checkedZone(restaurant, request.timezone());
        return preview(restaurant, newZone);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.SERIALIZABLE)
    public RestaurantResponse change(Authentication authentication, UUID restaurantId, TimezoneChangeDtos.ChangeRequest request) {
        Restaurant restaurant=restaurantScopeService.requirePlatformManagedRestaurant(authentication,restaurantId,ONLY_SUPER_ADMIN);
        changes.lock(restaurant);
        RestaurantResponse replay=changes.replay(restaurantId,"TIMEZONE",request.previewToken(),request,RestaurantResponse.class);
        if(replay!=null)return replay;
        ZoneId oldZone=ZoneId.of(restaurant.getTimezone()),newZone=checkedZone(restaurant,request.timezone());
        changes.requireCurrent(request.previewToken(),fingerprint(restaurant,newZone));
        OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);
        var live=bookings.findAllByRestaurant_IdAndStatusInAndReservationEndAfterOrderByIdAsc(restaurantId,
            java.util.EnumSet.of(pos.pos.reservation.enums.ReservationStatus.PENDING,pos.pos.reservation.enums.ReservationStatus.CONFIRMED,
                pos.pos.reservation.enums.ReservationStatus.CHECKED_IN,pos.pos.reservation.enums.ReservationStatus.SEATED),now);
        if(restaurantUsageRepository.openOrders(restaurantId)>0 || restaurantUsageRepository.openShifts(restaurantId)>0
                || live.stream().anyMatch(r->!r.getReservationStart().isAfter(now)||r.getStatus()==pos.pos.reservation.enums.ReservationStatus.CHECKED_IN||r.getStatus()==pos.pos.reservation.enums.ReservationStatus.SEATED))
            throw new AuthException("Finish active orders, shifts and seated or arrived bookings before changing the time zone.",HttpStatus.CONFLICT);
        if(!live.isEmpty()&&!Boolean.TRUE.equals(request.confirmed()))throw new AuthException("Review and confirm the upcoming bookings first.",HttpStatus.CONFLICT);
        boolean keepClock=!Boolean.FALSE.equals(request.keepBookingTimes());
        var money=restaurantUsageRepository.moneyInFlight(restaurantId,now);
        if(keepClock&&(money.pendingBookingPayments()>0||money.depositsOnUpcomingBookings()>0||money.waitingPreOrders()>0))
            throw new AuthException("Resolve booking payments, deposits and pre-orders before moving their agreed times, or keep the exact moment.",HttpStatus.CONFLICT);
        UUID actor=restaurantScopeService.currentUserId(authentication);
        for(var booking:live){
            OffsetDateTime oldStart=booking.getReservationStart();
            OffsetDateTime start=keepClock?TimezoneBookingGuard.keepClock(oldStart,oldZone,newZone):oldStart;
            OffsetDateTime end=keepClock?TimezoneBookingGuard.keepClock(booking.getReservationEnd(),oldZone,newZone):booking.getReservationEnd();
            if(!start.isAfter(now)||!end.isAfter(start)||end.isAfter(start.plusHours(24)))
                throw new AuthException("A booking would move into the past or have an invalid duration. Resolve it first.",HttpStatus.CONFLICT);
            bookingGuard.openingHours(booking.getBranch().getId(),start,end,newZone);
            booking.setReservationStart(start);booking.setReservationEnd(end);
            if(keepClock&&booking.getHoldUntil()!=null)booking.setHoldUntil(booking.getHoldUntil().plus(java.time.Duration.between(oldStart,start)));
            booking.setScheduleChangedAt(now);booking.setUpdatedBy(actor);
        }
        restaurant.setTimezone(newZone.getId());restaurant.setUpdatedBy(actor);
        // Revalidate against all final booking windows, not a mix of old and new ones.
        entities.flush();
        for(var booking:live)tables.revalidateCurrentTables(booking);
        // Daily jobs derive future deadlines from the new booking instants/zone; allow a new daily summary.
        jdbc.update("delete from "+schema()+"reservation_reminders where restaurant_id=:r and service_date>=:today",
            Map.of("r",restaurantId,"today",now.atZoneSameInstant(newZone).toLocalDate()));
        jdbc.update("insert into "+schema()+"restaurant_timezone_changes(id,restaurant_id,old_timezone,new_timezone,keep_booking_times,bookings_moved,changed_by) values (:id,:r,:old,:new,:keep,:count,:actor)",
            Map.of("id",UUID.randomUUID(),"r",restaurantId,"old",oldZone.getId(),"new",newZone.getId(),"keep",keepClock,"count",live.size(),"actor",actor));
        RestaurantResponse result=restaurantMapper.toResponse(restaurantRepository.saveAndFlush(restaurant));
        changes.completed(restaurantId,"TIMEZONE",request.previewToken(),request,result);
        return result;
    }

    private String fingerprint(Restaurant restaurant,ZoneId newZone){
        var params=Map.of("r",restaurant.getId());
        var rows=jdbc.queryForList("select id,status,reservation_start,reservation_end,updated_at from "+schema()+"reservations where restaurant_id=:r and "+LIVE_BOOKING+" order by id",params);
        var hours=jdbc.queryForList("select h.* from "+schema()+"\"settings-business-hours\" h join "+schema()+"branches b on b.id=h.branch_id where b.restaurant_id=:r order by h.id",params);
        var specials=jdbc.queryForList("select h.* from "+schema()+"\"settings-special-hours\" h join "+schema()+"branches b on b.id=h.branch_id where b.restaurant_id=:r order by h.id",params);
        return changes.hash(restaurant.getId(),restaurant.getTimezone(),restaurant.getUpdatedAt(),newZone.getId(),rows,hours,specials);
    }

    private TimezoneChangeDtos.Preview preview(Restaurant restaurant, ZoneId newZone) {
        ZoneId oldZone = ZoneId.of(restaurant.getTimezone());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Instant instant = now.toInstant();
        int difference = (newZone.getRules().getOffset(instant).getTotalSeconds() - oldZone.getRules().getOffset(instant).getTotalSeconds()) / 60;
        long upcoming = restaurantUsageRepository.upcomingBookings(restaurant.getId(), now);
        List<TimezoneChangeDtos.BookingExample> examples = restaurantUsageRepository.nextBookings(restaurant.getId(), now, 5).stream()
                .map(booking -> {
                    LocalDateTime before = booking.start().atZoneSameInstant(oldZone).toLocalDateTime();
                    LocalDateTime keepingMoment = booking.start().atZoneSameInstant(newZone).toLocalDateTime();
                    return new TimezoneChangeDtos.BookingExample(booking.code(), booking.name(), EXAMPLE.format(before),
                            EXAMPLE.format(keepingMoment), EXAMPLE.format(before));
                })
                .toList();
        List<String> notes = List.of(
                "Opening hours, menu hours and event nights keep their clock times.",
                "Orders, payments and shifts already recorded keep their exact moment, so they show "
                        + describe(difference) + " in the new time zone.",
                "Reports count days from midnight in the new time zone from now on.");
        return new TimezoneChangeDtos.Preview(oldZone.getId(), newZone.getId(), difference, upcoming,
                restaurantUsageRepository.openOrders(restaurant.getId()), restaurantUsageRepository.openShifts(restaurant.getId()),
                upcoming > 0, examples, notes, fingerprint(restaurant,newZone));
    }

    private static String describe(int minutes) {
        if (minutes == 0) return "the same clock times";
        int hours = Math.abs(minutes) / 60;
        int rest = Math.abs(minutes) % 60;
        String amount = (hours > 0 ? hours + (hours == 1 ? " hour" : " hours") : "") + (hours > 0 && rest > 0 ? " " : "")
                + (rest > 0 ? rest + " min" : "");
        return amount + (minutes > 0 ? " later" : " earlier");
    }

    private ZoneId checkedZone(Restaurant restaurant, String requested) {
        String normalized = RestaurantFieldNormalizer.normalizeTimezone(requested);
        if (!RestaurantFieldNormalizer.isValidTimezone(normalized)) {
            throw new AuthException("Choose a time zone from the list (for example Europe/Tirane)", HttpStatus.BAD_REQUEST);
        }
        if (normalized.equals(restaurant.getTimezone())) {
            throw new AuthException("The restaurant already uses " + normalized, HttpStatus.BAD_REQUEST);
        }
        return ZoneId.of(normalized);
    }

    private String schema() {
        if (!schemaName.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalStateException("Invalid schema");
        return "\"" + schemaName + "\".";
    }
}
