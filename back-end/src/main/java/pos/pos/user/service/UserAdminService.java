package pos.pos.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.auth.entity.UserSession;
import pos.pos.auth.enums.SessionRevocationReason;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.common.dto.PageResponse;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.auth.PhoneAlreadyExistsException;
import pos.pos.exception.role.RoleNotFoundException;
import pos.pos.exception.user.UserNotFoundException;
import pos.pos.role.dto.RoleResponse;
import pos.pos.role.entity.Role;
import pos.pos.role.mapper.RoleMapper;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.rbac.RoleHierarchyService;
import pos.pos.user.dto.ReplaceUserRolesRequest;
import pos.pos.user.dto.UpdateUserRequest;
import pos.pos.user.dto.UserResponse;
import pos.pos.user.entity.User;
import pos.pos.user.entity.UserRole;
import pos.pos.user.mapper.UserMapper;
import pos.pos.user.repository.UserRepository;
import pos.pos.user.repository.UserRoleRepository;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";
    private static final String STATUS_DELETED = "DELETED";
    private static final Comparator<Role> ACTIVE_ROLE_ORDER =
            Comparator.comparingLong(Role::getRank).reversed().thenComparing(Role::getName);

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final RoleHierarchyService roleHierarchyService;
    private final UserSessionRepository userSessionRepository;

    public PageResponse<UserResponse> getUsers(
            Authentication authentication,
            String search,
            Boolean active,
            String roleCode,
            Integer page,
            Integer size,
            String sortBy,
            String direction
    ) {
        Pageable pageable = PageRequest.of(
                page == null ? 0 : page,
                size == null ? DEFAULT_PAGE_SIZE : size,
                Sort.by(resolveDirection(direction), resolveSortProperty(sortBy))
        );

        String normalizedSearch = normalizeSearch(search);
        String searchLike = normalizedSearch == null ? null : "%" + normalizedSearch + "%";
        String normalizedPhoneLike = normalizedSearch == null ? null : "%" + NormalizationUtils.normalizePhone(search) + "%";
        String normalizedRoleCode = NormalizationUtils.normalizeUpper(roleCode);

        Page<User> usersPage = userRepository.searchVisibleUsers(
                active,
                searchLike,
                normalizedPhoneLike,
                normalizedRoleCode,
                roleHierarchyService.isSuperAdmin(authentication),
                roleHierarchyService.actorRank(authentication),
                pageable
        );

        Map<UUID, List<String>> roleCodesByUserId = loadActiveRoleCodes(usersPage.getContent());
        List<UserResponse> items = usersPage.getContent().stream()
                .map(user -> userMapper.toUserResponse(user, roleCodesByUserId.getOrDefault(user.getId(), List.of())))
                .toList();

        return PageResponse.from(new PageImpl<>(items, pageable, usersPage.getTotalElements()));
    }

    public UserResponse getUser(Authentication authentication, UUID userId) {
        User user = findExistingUser(userId);
        roleHierarchyService.assertCanManageUser(authentication, userId);

        return userMapper.toUserResponse(user, roleRepository.findActiveRoleCodesByUserId(userId));
    }

    public UserResponse getUserByIdentifier(Authentication authentication, String identifier) {
        User user = findExistingUserByIdentifier(identifier);
        roleHierarchyService.assertCanManageUser(authentication, user.getId());

        return userMapper.toUserResponse(user, roleRepository.findActiveRoleCodesByUserId(user.getId()));
    }

    @Transactional
    public UserResponse updateUser(Authentication authentication, UUID userId, UpdateUserRequest request) {
        User user = findExistingUser(userId);
        roleHierarchyService.assertCanManageUser(authentication, userId);

        String newNormalizedPhone = NormalizationUtils.normalizePhone(request.getPhone());
        String currentNormalizedPhone = user.getNormalizedPhone();
        if (newNormalizedPhone != null
                && !newNormalizedPhone.equals(currentNormalizedPhone)
                && userRepository.existsByNormalizedPhoneAndIdNotAndDeletedAtIsNull(newNormalizedPhone, userId)) {
            throw new PhoneAlreadyExistsException();
        }

        boolean phoneChanged = !java.util.Objects.equals(currentNormalizedPhone, newNormalizedPhone);
        boolean wasActive = user.isActive();

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPhone(request.getPhone());
        user.setActive(Boolean.TRUE.equals(request.getIsActive()));
        user.setStatus(user.isActive() ? STATUS_ACTIVE : STATUS_INACTIVE);
        user.setUpdatedBy(roleHierarchyService.currentUserId(authentication));

        if (phoneChanged) {
            user.setPhoneVerified(false);
            user.setPhoneVerifiedAt(null);
        }

        userRepository.save(user);

        if (wasActive && !user.isActive()) {
            revokeAllActiveSessions(userId);
        }

        return userMapper.toUserResponse(user, roleRepository.findActiveRoleCodesByUserId(userId));
    }

    public List<RoleResponse> getUserRoles(Authentication authentication, UUID userId) {
        findExistingUser(userId);
        roleHierarchyService.assertCanManageUser(authentication, userId);

        return roleRepository.findActiveRolesByUserId(userId).stream()
                .map(RoleMapper::toResponse)
                .toList();
    }

    @Transactional
    public UserResponse replaceUserRoles(Authentication authentication, UUID userId, ReplaceUserRolesRequest request) {
        findExistingUser(userId);
        roleHierarchyService.assertCanManageUser(authentication, userId);

        Set<UUID> requestedRoleIds = new LinkedHashSet<>(request.getRoleIds());
        List<Role> requestedRoles = roleRepository.findByIdIn(List.copyOf(requestedRoleIds)).stream()
                .filter(Role::isActive)
                .sorted(ACTIVE_ROLE_ORDER)
                .toList();

        if (requestedRoles.size() != requestedRoleIds.size()) {
            throw new RoleNotFoundException();
        }

        requestedRoles.forEach(role -> roleHierarchyService.assertCanAssignRole(authentication, role));

        UUID actorId = roleHierarchyService.currentUserId(authentication);
        List<UserRole> existingAssignments = userRoleRepository.findByUserId(userId);
        Map<UUID, UserRole> existingByRoleId = existingAssignments.stream()
                .collect(Collectors.toMap(UserRole::getRoleId, Function.identity(), (left, right) -> left));

        List<UserRole> assignmentsToRemove = existingAssignments.stream()
                .filter(assignment -> !requestedRoleIds.contains(assignment.getRoleId()))
                .toList();

        if (!assignmentsToRemove.isEmpty()) {
            userRoleRepository.deleteAll(assignmentsToRemove);
        }

        List<UserRole> assignmentsToAdd = requestedRoles.stream()
                .filter(role -> !existingByRoleId.containsKey(role.getId()))
                .map(role -> UserRole.builder()
                        .userId(userId)
                        .roleId(role.getId())
                        .assignedBy(actorId)
                        .build())
                .toList();

        if (!assignmentsToAdd.isEmpty()) {
            userRoleRepository.saveAll(assignmentsToAdd);
        }

        User user = findExistingUser(userId);
        return userMapper.toUserResponse(
                user,
                requestedRoles.stream().map(Role::getCode).toList()
        );
    }

    @Transactional
    public void deleteUser(Authentication authentication, UUID userId) {
        User user = findExistingUser(userId);
        roleHierarchyService.assertCanManageUser(authentication, userId);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        user.setActive(false);
        user.setStatus(STATUS_DELETED);
        user.setDeletedAt(now);
        user.setUpdatedBy(roleHierarchyService.currentUserId(authentication));
        userRepository.save(user);

        revokeAllActiveSessions(userId);
    }

    private User findExistingUser(UUID userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(UserNotFoundException::new);
    }

    private User findExistingUserByIdentifier(String identifier) {
        String normalizedIdentifier = NormalizationUtils.normalizeLower(identifier);
        if (normalizedIdentifier == null) {
            throw new UserNotFoundException();
        }

        return findUserByIdentifier(normalizedIdentifier)
                .orElseThrow(UserNotFoundException::new);
    }

    private java.util.Optional<User> findUserByIdentifier(String normalizedIdentifier) {
        if (normalizedIdentifier.contains("@")) {
            return userRepository.findByEmailAndDeletedAtIsNull(normalizedIdentifier);
        }

        return userRepository.findByUsernameAndDeletedAtIsNull(normalizedIdentifier);
    }

    private void revokeAllActiveSessions(UUID userId) {
        userSessionRepository.revokeAllActiveSessionsByUserId(
                userId,
                OffsetDateTime.now(ZoneOffset.UTC),
                SessionRevocationReason.SESSION_REVOKED.name()
        );
    }

    private Map<UUID, List<String>> loadActiveRoleCodes(Collection<User> users) {
        if (users.isEmpty()) {
            return Map.of();
        }

        List<UserRole> assignments = userRoleRepository.findByUserIdIn(
                users.stream().map(User::getId).toList()
        );
        if (assignments.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Role> rolesById = roleRepository.findByIdIn(
                        assignments.stream().map(UserRole::getRoleId).distinct().toList()
                ).stream()
                .filter(Role::isActive)
                .collect(Collectors.toMap(Role::getId, Function.identity()));

        return assignments.stream()
                .filter(assignment -> rolesById.containsKey(assignment.getRoleId()))
                .collect(Collectors.groupingBy(
                        UserRole::getUserId,
                        Collectors.collectingAndThen(Collectors.toList(), userAssignments ->
                                userAssignments.stream()
                                        .map(assignment -> rolesById.get(assignment.getRoleId()))
                                        .filter(java.util.Objects::nonNull)
                                        .sorted(ACTIVE_ROLE_ORDER)
                                        .map(Role::getCode)
                                        .toList()
                        )
                ));
    }

    private String resolveSortProperty(String sortBy) {
        String normalized = NormalizationUtils.normalizeLower(sortBy);
        if (normalized == null || normalized.equals("createdat") || normalized.equals("created_at")) {
            return "createdAt";
        }

        return switch (normalized) {
            case "updatedat", "updated_at" -> "updatedAt";
            case "firstname", "first_name" -> "firstName";
            case "lastname", "last_name" -> "lastName";
            case "email" -> "email";
            case "username" -> "username";
            default -> throw new AuthException("Invalid sortBy value", HttpStatus.BAD_REQUEST);
        };
    }

    private Sort.Direction resolveDirection(String direction) {
        try {
            return Sort.Direction.fromString(
                    NormalizationUtils.normalize(direction) == null ? "desc" : direction
            );
        } catch (IllegalArgumentException ex) {
            throw new AuthException("Invalid sort direction", HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeSearch(String search) {
        return NormalizationUtils.normalizeLower(search);
    }
}
