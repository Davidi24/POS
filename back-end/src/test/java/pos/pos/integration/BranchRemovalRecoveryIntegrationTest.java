package pos.pos.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import pos.pos.integration.support.AbstractPosApiIntegrationTest;
import pos.pos.restaurant.entity.Branch;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class BranchRemovalRecoveryIntegrationTest extends AbstractPosApiIntegrationTest {
    private Branch branch(World w,String name) {var b=new Branch();b.setRestaurant(w.restaurant());b.setName(name);b.setCode("B"+next());return branchRepository.saveAndFlush(b);}
    private String path(World w,Branch b) {return "/restaurants/"+w.restaurantId()+"/branches/"+b.getId();}
    private Map<String,Object> request(World w,Branch b,String admin,String action) throws Exception {
        JsonNode p=get(path(w,b)+"/removal-check").as(admin).expect(200).get("archive");
        return Map.of("staffAction",action,"expectedVersion",p.get("expectedVersion").asLong(),"confirmedName",b.getName(),"previewToken",p.get("previewToken").asText());
    }
    @Test void keptStaffCanRecoverByReassignmentButCannotReturnToRemovedOrClosedBranch() throws Exception {
        var w=newWorld("reassign");var b=branch(w,"Remove");var closed=branch(w,"Closed");var admin=superAdminToken();
        var user=staff(w.restaurant(),b,"waiter","WAITER");String access=token(user);
        delete(path(w,b)).as(admin).body(request(w,b,admin,"KEEP")).expect(204);
        assertThat(get("/auth/me").as(access).expect(200).get("defaultBranchId").isNull()).isTrue();
        var update=new HashMap<String,Object>(Map.of("firstName","Test","lastName","Waiter","isActive",true,"defaultBranchId",b.getId()));
        put("/users/{id}",user.getId()).as(admin).body(update).expect(400);
        jdbcTemplate.update("update pos_api_it.branches set is_active=false,status='INACTIVE' where id=?",closed.getId());
        update.put("defaultBranchId",closed.getId());put("/users/{id}",user.getId()).as(admin).body(update).expect(409);
        update.put("defaultBranchId",w.branchId());put("/users/{id}",user.getId()).as(admin).body(update).expect(200);
        assertThat(get("/auth/me").as(access).expect(200).get("defaultBranchId").asText()).isEqualTo(w.branchId().toString());
    }
    @Test void failureAtTheLastWriteRollsBackStaffSessionAndSetupChanges() throws Exception {
        var w=newWorld("remove-rollback");var b=branch(w,"Remove");var admin=superAdminToken();var staff=staff(w.restaurant(),b,"staff","WAITER");String access=token(staff);
        UUID device=UUID.randomUUID();jdbcTemplate.update("insert into pos_api_it.devices(id,restaurant_id,branch_id,code,name,device_type,status,is_active,is_online,created_at,updated_at) values (?,?,?,'POS','Till','POS_TERMINAL','ACTIVE',true,true,now(),now())",device,w.restaurantId(),b.getId());
        var request=request(w,b,admin,"DEACTIVATE");String name="qa_removal_"+next();
        jdbcTemplate.execute("create function pos_api_it."+name+"() returns trigger language plpgsql as $$ begin raise exception 'QA rollback' using errcode='23514'; end $$");
        jdbcTemplate.execute("create trigger "+name+" before update on pos_api_it.branches for each row when (new.id='"+b.getId()+"'::uuid and new.deleted_at is not null) execute function pos_api_it."+name+"()");
        try {
            delete(path(w,b)).as(admin).body(request).expect(400);
            var saved=userRepository.findById(staff.getId()).orElseThrow();assertThat(saved.isActive()).isTrue();assertThat(saved.getDefaultBranchId()).isEqualTo(b.getId());
            get("/auth/me").as(access).expect(200);
            assertThat(branchRepository.findById(b.getId()).orElseThrow().getDeletedAt()).isNull();
            assertThat(jdbcTemplate.queryForObject("select status from pos_api_it.devices where id=?",String.class,device)).isEqualTo("ACTIVE");
        } finally {
            jdbcTemplate.execute("drop trigger "+name+" on pos_api_it.branches");jdbcTemplate.execute("drop function pos_api_it."+name+"()");
        }
        delete(path(w,b)).as(admin).body(request(w,b,admin,"DEACTIVATE")).expect(204);
    }
    @Test void openShiftAndUpcomingBookingEachPreventRemoval() throws Exception {
        var w=newWorld("remove-live-work");var b=branch(w,"Remove");var admin=superAdminToken();
        var clocked=post(path(w,b)+"/shifts/clock-in").as(w.ownerToken()).body(Map.of()).expect(200);
        var p=get(path(w,b)+"/removal-check").as(admin).expect(200).get("archive");assertThat(p.get("reasons").toString()).contains("open shifts");
        delete(path(w,b)).as(admin).body(request(w,b,admin,"KEEP")).expect(409);
        jdbcTemplate.update("update pos_api_it.shifts set status='SCHEDULED' where branch_id=?",b.getId());
        p=get(path(w,b)+"/removal-check").as(admin).expect(200).get("archive");assertThat(p.get("reasons").toString()).contains("scheduled shifts");
        delete(path(w,b)).as(admin).body(request(w,b,admin,"KEEP")).expect(409);
        jdbcTemplate.update("update pos_api_it.shifts set status='CLOSED',ended_at=now() where branch_id=?",b.getId());
        var start=java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusDays(1).withNano(0);
        post("/restaurants/{r}/reservations",w.restaurantId()).as(admin).body(Map.of("branchId",b.getId(),"partySize",2,"contactName","QA booking","reservationStart",start.toString(),"reservationEnd",start.plusHours(1).toString())).expect(201);
        p=get(path(w,b)+"/removal-check").as(admin).expect(200).get("archive");assertThat(p.get("reasons").toString()).contains("unfinished bookings");
        delete(path(w,b)).as(admin).body(request(w,b,admin,"KEEP")).expect(409);
    }
}
