package pos.pos.customer.mapper;

import org.springframework.stereotype.Component;
import pos.pos.customer.dto.CustomerRequest;
import pos.pos.customer.dto.CustomerResponse;
import pos.pos.customer.entity.Customer;

@Component
public class CustomerMapper {

    public void applyRequest(Customer customer, CustomerRequest request) {
        customer.setCode(request.getCode());
        customer.setFirstName(request.getFirstName());
        customer.setLastName(request.getLastName());
        customer.setEmail(request.getEmail());
        customer.setPhone(request.getPhone());
        customer.setNotes(request.getNotes());
        customer.setActive(request.getActive() == null || request.getActive());
    }

    public CustomerResponse toResponse(Customer customer) {
        if (customer == null) {
            return null;
        }

        return CustomerResponse.builder()
                .id(customer.getId())
                .restaurantId(customer.getRestaurant() == null ? null : customer.getRestaurant().getId())
                .code(customer.getCode())
                .firstName(customer.getFirstName())
                .lastName(customer.getLastName())
                .fullName(fullName(customer))
                .email(customer.getEmail())
                .phone(customer.getPhone())
                .notes(customer.getNotes())
                .active(customer.isActive())
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .build();
    }

    private String fullName(Customer customer) {
        String firstName = customer.getFirstName();
        String lastName = customer.getLastName();
        if (firstName == null && lastName == null) {
            return null;
        }
        if (firstName == null) {
            return lastName;
        }
        if (lastName == null) {
            return firstName;
        }
        return firstName + " " + lastName;
    }
}
