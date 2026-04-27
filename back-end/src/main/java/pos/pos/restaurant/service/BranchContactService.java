package pos.pos.restaurant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.restaurant.BranchContactNotFoundException;
import pos.pos.restaurant.dto.ContactResponse;
import pos.pos.restaurant.dto.UpsertContactRequest;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.BranchContact;
import pos.pos.restaurant.mapper.BranchContactMapper;
import pos.pos.restaurant.repository.BranchContactRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BranchContactService {

    private final RestaurantScopeService restaurantScopeService;
    private final BranchContactRepository branchContactRepository;
    private final BranchContactMapper branchContactMapper;

    @Transactional(readOnly = true)
    public List<ContactResponse> getContacts(Authentication authentication, UUID restaurantId, UUID branchId) {
        restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        return branchContactRepository.findAllByBranchIdAndDeletedAtIsNullOrderByIsPrimaryDescCreatedAtAsc(branchId)
                .stream()
                .map(branchContactMapper::toResponse)
                .toList();
    }

    @Transactional
    public ContactResponse createContact(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UpsertContactRequest request
    ) {
        Branch branch = restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        if (Boolean.TRUE.equals(request.getIsPrimary())) {
            clearExistingPrimary(branchId, null, actorId);
        }

        BranchContact contact = branchContactMapper.toNewEntity(branch, request, actorId);
        branchContactRepository.save(contact);
        return branchContactMapper.toResponse(contact);
    }

    @Transactional
    public ContactResponse updateContact(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID contactId,
            UpsertContactRequest request
    ) {
        restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        BranchContact contact = findExistingContact(branchId, contactId);
        if (Boolean.TRUE.equals(request.getIsPrimary())) {
            clearExistingPrimary(branchId, contactId, actorId);
        }

        branchContactMapper.updateEntity(contact, request, actorId);
        branchContactRepository.save(contact);
        return branchContactMapper.toResponse(contact);
    }

    @Transactional
    public ContactResponse makePrimary(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID contactId
    ) {
        restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        BranchContact contact = findExistingContact(branchId, contactId);
        clearExistingPrimary(branchId, contactId, actorId);
        branchContactMapper.updatePrimary(contact, true, actorId);
        branchContactRepository.save(contact);
        return branchContactMapper.toResponse(contact);
    }

    @Transactional
    public void deleteContact(Authentication authentication, UUID restaurantId, UUID branchId, UUID contactId) {
        restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        BranchContact contact = findExistingContact(branchId, contactId);
        branchContactMapper.markDeleted(
                contact,
                restaurantScopeService.currentUserId(authentication),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        branchContactRepository.save(contact);
    }

    private BranchContact findExistingContact(UUID branchId, UUID contactId) {
        return branchContactRepository.findByIdAndBranchIdAndDeletedAtIsNull(contactId, branchId)
                .orElseThrow(BranchContactNotFoundException::new);
    }

    private void clearExistingPrimary(UUID branchId, UUID keepContactId, UUID actorId) {
        branchContactRepository.clearAllPrimary(branchId, actorId);
    }
}
