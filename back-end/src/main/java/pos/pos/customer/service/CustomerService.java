package pos.pos.customer.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.customer.dto.CustomerRequest;
import pos.pos.customer.dto.CustomerResponse;
import pos.pos.customer.entity.Customer;
import pos.pos.customer.mapper.CustomerMapper;
import pos.pos.customer.repository.CustomerRepository;
import pos.pos.common.dto.PageResponse;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.customer.CustomerNotFoundException;
import pos.pos.reservation.dto.ReservationResponse;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.mapper.ReservationMapper;
import pos.pos.reservation.repository.ReservationRepository;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.utils.NormalizationUtils;
import pos.pos.utils.PageableUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@lombok.RequiredArgsConstructor
public class CustomerService {

    private final RestaurantScopeService restaurantScopeService;
    private final CustomerRepository customerRepository;
    private final ReservationRepository reservationRepository;
    private final CustomerMapper customerMapper;
    private final ReservationMapper reservationMapper;

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> getCustomers(Authentication authentication, UUID restaurantId, Integer page, Integer size) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        PageRequest pageable = PageableUtils.of(page, size, 50, Sort.by(
                Sort.Order.asc("firstName"),
                Sort.Order.asc("lastName"),
                Sort.Order.asc("id")
        ));
        return PageResponse.from(customerRepository.findAllByRestaurant_IdAndDeletedAtIsNull(restaurantId, pageable)
                .map(customerMapper::toResponse));
    }

    @Transactional
    public CustomerResponse createCustomer(Authentication authentication, UUID restaurantId, CustomerRequest request) {
        Restaurant restaurant = restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);

        assertCustomerCodeAvailable(restaurantId, request.getCode(), null);

        Customer customer = new Customer();
        customer.setRestaurant(restaurant);
        customer.setCreatedBy(actorId);
        customer.setUpdatedBy(actorId);
        customerMapper.applyRequest(customer, request);

        return customerMapper.toResponse(saveCustomer(customer));
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(Authentication authentication, UUID restaurantId, UUID customerId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return customerMapper.toResponse(requireCustomer(restaurantId, customerId));
    }

    @Transactional
    public CustomerResponse updateCustomer(
            Authentication authentication,
            UUID restaurantId,
            UUID customerId,
            CustomerRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Customer customer = requireCustomer(restaurantId, customerId);

        assertCustomerCodeAvailable(restaurantId, request.getCode(), customer.getCode());

        customer.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        customerMapper.applyRequest(customer, request);

        return customerMapper.toResponse(saveCustomer(customer));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> getCustomerReservations(
            Authentication authentication,
            UUID restaurantId,
            UUID customerId,
            Integer page,
            Integer size
    ) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        requireCustomer(restaurantId, customerId);

        PageRequest pageable = PageableUtils.of(page, size, 50, Sort.unsorted());
        Page<UUID> ids = reservationRepository.findReservationIdsForCustomer(customerId, restaurantId, pageable);
        Map<UUID, Reservation> reservationsById = new HashMap<>();
        if (!ids.isEmpty()) {
            reservationRepository.findAllByIdIn(ids.getContent()).forEach(reservation -> reservationsById.put(reservation.getId(), reservation));
        }
        var items = ids.getContent().stream()
                .map(reservationsById::get)
                .filter(java.util.Objects::nonNull)
                .map(reservation -> reservationMapper.toResponse(reservation, reservation.getTableAssignments()))
                .toList();
        return PageResponse.from(new PageImpl<>(items, ids.getPageable(), ids.getTotalElements()));
    }

    @Transactional
    public void deleteCustomer(Authentication authentication, UUID restaurantId, UUID customerId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Customer customer = requireCustomer(restaurantId, customerId);

        customer.setActive(false);
        customer.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        customer.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        saveCustomer(customer);
    }

    private Customer requireCustomer(UUID restaurantId, UUID customerId) {
        return customerRepository.findByIdAndRestaurant_IdAndDeletedAtIsNull(customerId, restaurantId)
                .orElseThrow(CustomerNotFoundException::new);
    }

    private void assertCustomerCodeAvailable(UUID restaurantId, String rawCode, String existingCode) {
        String normalizedCode = NormalizationUtils.normalizeCode(rawCode, 50);
        if (normalizedCode == null || normalizedCode.equals(existingCode)) {
            return;
        }

        if (customerRepository.existsByRestaurant_IdAndCode(restaurantId, normalizedCode)) {
            throw new AuthException("Customer code already exists in this restaurant", HttpStatus.CONFLICT);
        }
    }

    private boolean isCustomerCodeConstraintViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && "uk_customers_restaurant_code".equals(violation.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private Customer saveCustomer(Customer customer) {
        try {
            return customerRepository.saveAndFlush(customer);
        } catch (DataIntegrityViolationException ex) {
            if (isCustomerCodeConstraintViolation(ex)) {
                throw new AuthException("Customer code already exists in this restaurant", HttpStatus.CONFLICT);
            }
            throw new AuthException("Customer update violates a data constraint", HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}
