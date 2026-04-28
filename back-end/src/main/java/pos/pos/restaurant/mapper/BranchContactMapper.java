package pos.pos.restaurant.mapper;

import org.springframework.stereotype.Component;
import pos.pos.restaurant.dto.ContactResponse;
import pos.pos.restaurant.dto.UpsertContactRequest;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.BranchContact;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class BranchContactMapper {

    public ContactResponse toResponse(BranchContact contact) {
        if (contact == null) {
            return null;
        }

        return ContactResponse.builder()
                .id(contact.getId())
                .contactType(contact.getContactType())
                .fullName(contact.getFullName())
                .email(contact.getEmail())
                .phone(contact.getPhone())
                .isPrimary(contact.isPrimary())
                .jobTitle(contact.getJobTitle())
                .createdAt(contact.getCreatedAt())
                .updatedAt(contact.getUpdatedAt())
                .build();
    }

    public BranchContact toNewEntity(Branch branch, UpsertContactRequest request, UUID actorId) {
        BranchContact contact = new BranchContact();
        contact.setBranch(branch);
        applyFields(contact, request);
        contact.setPrimary(Boolean.TRUE.equals(request.getIsPrimary()));
        contact.setCreatedBy(actorId);
        contact.setUpdatedBy(actorId);
        return contact;
    }

    public void updateEntity(BranchContact contact, UpsertContactRequest request, UUID actorId) {
        applyFields(contact, request);
        contact.setPrimary(Boolean.TRUE.equals(request.getIsPrimary()));
        contact.setUpdatedBy(actorId);
    }

    public void updatePrimary(BranchContact contact, boolean isPrimary, UUID actorId) {
        contact.setPrimary(isPrimary);
        contact.setUpdatedBy(actorId);
    }

    public void markDeleted(BranchContact contact, UUID actorId, OffsetDateTime deletedAt) {
        contact.setDeletedAt(deletedAt);
        contact.setUpdatedBy(actorId);
    }

    private void applyFields(BranchContact contact, UpsertContactRequest request) {
        contact.setContactType(request.getContactType());
        contact.setFullName(request.getFullName());
        contact.setEmail(request.getEmail());
        contact.setPhone(request.getPhone());
        contact.setJobTitle(request.getJobTitle());
    }
}
