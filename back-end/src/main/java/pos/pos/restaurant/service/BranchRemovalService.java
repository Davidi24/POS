package pos.pos.restaurant.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.exception.auth.AuthException;
import pos.pos.restaurant.dto.*;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.mapper.BranchMapper;
import pos.pos.restaurant.repository.BranchRepository;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.rbac.RoleHierarchyService;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** Explicit branch archival: preserve history, retire setup, and choose what happens to assigned staff. */
@Service
public class BranchRemovalService {
    private final JdbcTemplate jdbc;
    private final String schema;
    private final RestaurantScopeService scope;
    private final RestaurantClosureGuard guard;
    private final BranchRepository branches;
    private final BranchMapper mapper;
    private final UserRepository users;
    private final RoleRepository roles;
    private final RoleHierarchyService hierarchy;
    private final UserSessionRepository sessions;
    private final pos.pos.settings.service.SettingsAuditService audit;
    private final RestaurantDeviceRetirement deviceRetirement;

    public BranchRemovalService(JdbcTemplate jdbc, @Value("${spring.jpa.properties.hibernate.default_schema:public}") String schema,
            RestaurantScopeService scope, RestaurantClosureGuard guard, BranchRepository branches, BranchMapper mapper,
            UserRepository users, RoleRepository roles, RoleHierarchyService hierarchy, UserSessionRepository sessions, pos.pos.settings.service.SettingsAuditService audit,
            RestaurantDeviceRetirement deviceRetirement) {
        if(!schema.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid schema");
        this.jdbc=jdbc; this.schema="\""+schema+"\"."; this.scope=scope; this.guard=guard; this.branches=branches;
        this.mapper=mapper; this.users=users; this.roles=roles; this.hierarchy=hierarchy; this.sessions=sessions; this.audit=audit; this.deviceRetirement=deviceRetirement;
    }
    private record Staff(User user, boolean protectedAccount) {}

    @Transactional(readOnly=true)
    public BranchRemovalPlan preview(Authentication auth, UUID restaurantId, UUID branchId) {
        Branch branch=scope.requireAccessibleBranch(auth,restaurantId,branchId);
        return plan(auth,branch,staff(auth,branch,false));
    }

    private List<Staff> staff(Authentication auth, Branch branch, boolean lock) {
        var ids=jdbc.queryForList("select id from "+schema+"users where restaurant_id=? and default_branch_id=? and deleted_at is null order by id"+(lock?" for update":""),
                UUID.class,branch.getRestaurant().getId(),branch.getId());
        var result=new ArrayList<Staff>();
        for(UUID id:ids) {
            User user=users.findById(id).orElseThrow();
            boolean protectedAccount=Objects.equals(id,branch.getRestaurant().getOwnerId()) || Objects.equals(id,hierarchy.currentUserId(auth))
                || roles.findActiveRoleCodesByUserId(id).stream().anyMatch(Set.of("OWNER","CO_OWNER","SUPER_ADMIN","CO_SUPER_ADMIN")::contains)
                || count("select count(*) from "+schema+"branches where manager_user_id=? and id<>? and deleted_at is null",id,branch.getId())>0;
            result.add(new Staff(user,protectedAccount));
        }
        return result;
    }

    private BranchRemovalPlan plan(Authentication auth, Branch branch, List<Staff> staff) {
        UUID id=branch.getId();
        var reasons=new ArrayList<>(guard.archiveReasons(branch));
        if(branches.countByRestaurantIdAndDeletedAtIsNull(branch.getRestaurant().getId())<=1) reasons.add("the restaurant's only branch");
        long devices=count("select count(*) from "+schema+"devices where branch_id=? and status<>'RETIRED'",id);
        long tables=count("select count(*) from "+schema+"tables where branch_id=? and is_active=true",id);
        long stations=count("select count(*) from "+schema+"kds_stations where branch_id=? and is_active=true",id);
        long locations=count("select count(*) from "+schema+"inventory_locations where branch_id=? and is_active=true",id);
        boolean canDeactivate=auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("USERS_UPDATE"));
        for(Staff member:staff) if(!member.protectedAccount()) {
            try {hierarchy.assertCanManageUser(auth,member.user().getId());}
            catch(AuthException denied) {canDeactivate=false;}
        }
        String snapshot=id+":"+branch.getVersion()+":"+devices+":"+tables+":"+stations+":"+locations+":"+staff.stream()
                .map(s->s.user().getId()+":"+s.user().getVersion()+":"+s.protectedAccount()).toList();
        return new BranchRemovalPlan(reasons.isEmpty(),List.copyOf(reasons),staff.size(),staff.stream().filter(Staff::protectedAccount).count(),
                devices,tables,stations,locations,canDeactivate,branch.getVersion(),digest(snapshot));
    }

    @Transactional
    public void remove(Authentication auth, UUID restaurantId, UUID branchId, BranchRemovalRequest request) {
        scope.requirePlatformManagedRestaurant(auth,restaurantId,"Only platform administrators can remove branches");
        Branch branch=scope.requireManageableBranch(auth,restaurantId,branchId); // Parent and branch locked until commit.
        List<Staff> staff=staff(auth,branch,true);
        BranchRemovalPlan plan=plan(auth,branch,staff);
        if(!Objects.equals(request.expectedVersion(),branch.getVersion()) || !Objects.equals(request.previewToken(),plan.previewToken()))
            throw new AuthException("The branch or its staff changed. Review the removal options again.",HttpStatus.CONFLICT);
        if(!branch.getName().equals(request.confirmedName().trim())) throw new AuthException("Type the branch name to confirm removal",HttpStatus.BAD_REQUEST);
        if(!plan.canRemove()) throw new AuthException("Finish or move these first: "+String.join(", ",plan.reasons()),HttpStatus.CONFLICT);
        boolean deactivate=request.staffAction()==BranchRemovalRequest.StaffAction.DEACTIVATE;
        if(deactivate && !plan.canDeactivateStaff()) throw new AuthException("You do not have permission to deactivate these staff accounts",HttpStatus.FORBIDDEN);
        UUID actor=hierarchy.currentUserId(auth); OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);
        for(Staff member:staff) {
            User user=member.user();user.setDefaultBranchId(null);user.setUpdatedBy(actor);
            if(deactivate && !member.protectedAccount()) {
                user.setActive(false);user.setStatus("INACTIVE");
                sessions.revokeAllActiveSessionsByUserId(user.getId(),now,"BRANCH_REMOVED");
            }
            users.save(user);
        }
        deviceRetirement.retireBranch(branchId,actor,now);
        jdbc.update("update "+schema+"tables set is_active=false,status='OUT_OF_SERVICE',updated_at=?,updated_by=? where branch_id=?",now,actor,branchId);
        jdbc.update("update "+schema+"kds_station_routings set is_active=false,updated_at=? where station_id in (select id from "+schema+"kds_stations where branch_id=?)",now,branchId);
        jdbc.update("update "+schema+"kds_stations set is_active=false,updated_at=?,updated_by=? where branch_id=?",now,actor,branchId);
        jdbc.update("update "+schema+"inventory_locations set is_active=false,updated_at=?,updated_by=? where branch_id=?",now,actor,branchId);
        jdbc.update("update "+schema+"settings set default_branch_id=null,updated_at=?,updated_by=? where restaurant_id=? and default_branch_id=?",now,actor,restaurantId,branchId);
        mapper.markDeleted(branch,actor,now);branches.saveAndFlush(branch);
        audit.log(branch.getRestaurant(),branch,"BRANCH",branchId,"ARCHIVE",
                "Removed branch; staff choice="+request.staffAction()+"; assigned staff="+plan.staff()+"; protected accounts kept="+plan.protectedStaff()
                        +"; devices="+plan.devices()+"; tables="+plan.tables()+"; kitchen stations="+plan.kitchenStations()+"; stock locations="+plan.stockLocations()+". Historical records retained.",actor);
    }
    private long count(String sql,Object...args) {return Objects.requireNonNull(jdbc.queryForObject(sql,Long.class,args));}
    private static String digest(String value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible) {throw new IllegalStateException(impossible);}
    }
}
