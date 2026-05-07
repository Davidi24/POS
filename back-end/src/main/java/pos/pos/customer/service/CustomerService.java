package pos.pos.customer.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.customer.dto.CustomerRequest;
import pos.pos.customer.dto.CustomerResponse;
import pos.pos.customer.entity.Customer;
import pos.pos.customer.mapper.CustomerMapper;
import pos.pos.customer.repository.CustomerRepository;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.customer.CustomerNotFoundException;
import pos.pos.reservation.dto.ReservationResponse;
import pos.pos.reservation.mapper.ReservationMapper;
import pos.pos.reservation.repository.ReservationRepository;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
    public List<CustomerResponse> getCustomers(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return customerRepository.findAllByRestaurant_IdAndDeletedAtIsNullOrderByFirstNameAscLastNameAsc(restaurantId).stream()
                .map(customerMapper::toResponse)
                .toList();
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
    public List<ReservationResponse> getCustomerReservations(Authentication authentication, UUID restaurantId, UUID customerId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        requireCustomer(restaurantId, customerId);

        return reservationRepository.findAllByCustomer_IdAndRestaurant_IdOrderByReservationStartDesc(customerId, restaurantId).stream()
                .map(reservation -> reservationMapper.toResponse(reservation, reservation.getTableAssignments()))
                .toList();
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

        if (customerRepository.existsByRestaurant_IdAndCodeAndDeletedAtIsNull(restaurantId, normalizedCode)) {
            throw new AuthException("Customer code already exists in this restaurant", HttpStatus.CONFLICT);
        }
    }

    private Customer saveCustomer(Customer customer) {
        try {
            return customerRepository.saveAndFlush(customer);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException("Customer update violates a data constraint", HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}
