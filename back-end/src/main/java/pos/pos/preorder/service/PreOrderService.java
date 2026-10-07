package pos.pos.preorder.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.order.service.OrderSupport;
import pos.pos.preorder.dto.PreOrderActionRequest;
import pos.pos.preorder.dto.PreOrderRequest;
import pos.pos.preorder.dto.PreOrderResponse;
import pos.pos.preorder.entity.PreOrder;
import pos.pos.preorder.enums.PreOrderPaymentStatus;
import pos.pos.preorder.enums.PreOrderSource;
import pos.pos.preorder.enums.PreOrderStatus;
import pos.pos.preorder.mapper.PreOrderMapper;
import pos.pos.preorder.repository.PreOrderRepository;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.service.ReservationSupport;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.settings.entity.Settings;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static pos.pos.preorder.service.PreOrderLifecycleService.LIVE;
import static pos.pos.preorder.service.PreOrderLifecycleService.SENDABLE;

// Places, changes, cancels and sends pre-orders, for staff (by reservation id) and for guests (by reservation code).
// Rules: only for pending/confirmed bookings, only while the restaurant has pre-orders on, and only until the
// pre-order goes to the kitchen. Cancelling before then refunds it; after that it's locked.
@Service
@RequiredArgsConstructor
public class PreOrderService {

    static final int MAX_LIST_DAYS = 62;

    private static final Set<ReservationStatus> OPEN_FOR_PRE_ORDERS = EnumSet.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);

    private final RestaurantScopeService restaurantScopeService;
    private final ReservationSupport reservationSupport;
    private final OrderSupport orderSupport;
    private final PreOrderRepository preOrderRepository;
    private final PreOrderPricing preOrderPricing;
    private final PreOrderLifecycleService preOrderLifecycleService;
    private final PreOrderMapper preOrderMapper;

    @Transactional(readOnly = true)
    public PreOrderResponse getForReservation(Authentication authentication, UUID restaurantId, UUID reservationId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        return preOrderMapper.toResponse(requireLatest(reservationSupport.requireReservation(restaurantId, reservationId)));
    }

    @Transactional
    public PreOrderResponse placeForReservation(Authentication authentication, UUID restaurantId, UUID reservationId, PreOrderRequest request) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = reservationSupport.requireReservation(restaurantId, reservationId);
        return place(reservation, request, PreOrderSource.STAFF, restaurantScopeService.currentUserId(authentication));
    }

    @Transactional
    public PreOrderResponse cancelForReservation(Authentication authentication, UUID restaurantId, UUID reservationId, PreOrderActionRequest request) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = reservationSupport.requireReservation(restaurantId, reservationId);
        return cancel(reservation, request, restaurantScopeService.currentUserId(authentication));
    }

    // Staff override: fire it now instead of waiting for the lead time.
    @Transactional
    public PreOrderResponse sendNow(Authentication authentication, UUID restaurantId, UUID reservationId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = preOrderLifecycleService.lockFresh(reservationSupport.requireReservation(restaurantId, reservationId));
        PreOrder preOrder = requireLive(reservation);
        if (preOrder.getStatus() != PreOrderStatus.SCHEDULED) {
            throw new AuthException("This pre-order was already sent to the kitchen", HttpStatus.BAD_REQUEST);
        }
        if (!SENDABLE.contains(reservation.getStatus())) {
            throw new AuthException("The reservation is " + label(reservation.getStatus()) + ", so its pre-order can't be sent", HttpStatus.BAD_REQUEST);
        }
        preOrderLifecycleService.send(preOrder, restaurantScopeService.currentUserId(authentication), now());
        return preOrderMapper.toResponse(preOrder);
    }

    // For planning: every pre-order on bookings in the window, earliest booking first.
    @Transactional(readOnly = true)
    public List<PreOrderResponse> getForBranch(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            OffsetDateTime from,
            OffsetDateTime to,
            PreOrderStatus status
    ) {
        restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        orderSupport.requireCompleteWindow(from, to);
        // A kitchen plans days ahead, not years: a bounded window keeps the answer small.
        if (java.time.Duration.between(from, to).compareTo(java.time.Duration.ofDays(MAX_LIST_DAYS)) > 0) {
            throw new AuthException("Choose at most " + MAX_LIST_DAYS + " days", HttpStatus.BAD_REQUEST);
        }
        return preOrderRepository.findForBranch(restaurantId, branchId, from, to, status).stream()
                .map(preOrderMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PreOrderResponse getPublic(String reservationCode) {
        return preOrderMapper.toResponse(requireLatest(reservationSupport.requirePublicReservation(reservationCode)));
    }

    @Transactional
    public PreOrderResponse placePublic(String reservationCode, PreOrderRequest request) {
        return place(reservationSupport.requirePublicReservation(reservationCode), request, PreOrderSource.ONLINE, null);
    }

    @Transactional
    public PreOrderResponse cancelPublic(String reservationCode, PreOrderActionRequest request) {
        return cancel(reservationSupport.requirePublicReservation(reservationCode), request, null);
    }

    private PreOrderResponse place(Reservation reservation, PreOrderRequest request, PreOrderSource source, UUID actorId) {
        preOrderLifecycleService.lockFresh(reservation);
        Settings settings = orderSupport.loadSettings(reservation.getRestaurant());
        if (!settings.isPreOrdersEnabled()) {
            throw new AuthException("Pre-orders are not available for this restaurant", HttpStatus.BAD_REQUEST);
        }
        if (!OPEN_FOR_PRE_ORDERS.contains(reservation.getStatus())) {
            throw new AuthException("Pre-orders can only be placed for pending or confirmed reservations", HttpStatus.BAD_REQUEST);
        }

        PreOrder preOrder = preOrderRepository.lockLiveByReservationId(reservation.getId(), LIVE).orElse(null);
        if (preOrder != null && preOrder.getStatus() == PreOrderStatus.SENT) {
            throw new AuthException("This pre-order is already in the kitchen and can no longer be changed", HttpStatus.BAD_REQUEST);
        }
        int leadMinutes = preOrder == null ? settings.getPreOrderLeadMinutes() : preOrder.getLeadMinutes();
        if (!now().isBefore(reservation.getReservationStart().minusMinutes(leadMinutes))) {
            throw new AuthException("It's too late to pre-order for this reservation; please order at the table", HttpStatus.BAD_REQUEST);
        }

        if (preOrder == null) {
            preOrder = new PreOrder();
            preOrder.setRestaurant(reservation.getRestaurant());
            preOrder.setReservation(reservation);
            preOrder.setSource(source);
            preOrder.setLeadMinutes(leadMinutes);
            preOrder.setCreatedBy(actorId);
        }
        preOrderPricing.applyItems(preOrder, reservation, request.getItems(), source == PreOrderSource.ONLINE);
        preOrder.setNotes(request.getNotes());
        // Online payment isn't built yet: the full amount counts as paid whenever the pre-order is placed or changed.
        preOrder.setPaymentStatus(PreOrderPaymentStatus.PAID);
        preOrder.setPaidAmount(preOrder.getTotal());
        preOrder.setUpdatedBy(actorId);
        return preOrderMapper.toResponse(preOrderRepository.save(preOrder));
    }

    private PreOrderResponse cancel(Reservation reservation, PreOrderActionRequest request, UUID actorId) {
        preOrderLifecycleService.lockFresh(reservation);
        PreOrder preOrder = requireLive(reservation);
        OffsetDateTime now = now();
        if (preOrder.getStatus() == PreOrderStatus.SENT || !now.isBefore(preOrder.sendAt())) {
            throw new AuthException("The kitchen has started on this pre-order, so it can no longer be cancelled", HttpStatus.BAD_REQUEST);
        }
        String reason = request == null || request.getReason() == null || request.getReason().isBlank()
                ? "Cancelled before it went to the kitchen"
                : request.getReason();
        preOrderLifecycleService.refund(preOrder, reason, actorId, now);
        return preOrderMapper.toResponse(preOrder);
    }

    private PreOrder requireLive(Reservation reservation) {
        return preOrderRepository.lockLiveByReservationId(reservation.getId(), LIVE)
                .orElseThrow(() -> new AuthException("This reservation has no active pre-order", HttpStatus.NOT_FOUND));
    }

    private PreOrder requireLatest(Reservation reservation) {
        return preOrderRepository.findForReservation(reservation.getId(), LIVE).stream()
                .findFirst()
                .orElseThrow(() -> new AuthException("This reservation has no pre-order", HttpStatus.NOT_FOUND));
    }

    private static String label(ReservationStatus status) {
        return status.name().toLowerCase().replace('_', ' ');
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
