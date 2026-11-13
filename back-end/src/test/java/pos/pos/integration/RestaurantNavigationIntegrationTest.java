package pos.pos.integration;

import org.junit.jupiter.api.Test;
import pos.pos.integration.support.AbstractPosApiIntegrationTest;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.enums.BranchStatus;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class RestaurantNavigationIntegrationTest extends AbstractPosApiIntegrationTest {
    @Test void staleSelectionsRejectClosedInactiveDeletedAndMismatchedBranches() throws Exception {
        World w=newWorld("navigation");String admin=token(superAdmin());
        for (String status: List.of("INACTIVE","TEMPORARILY_CLOSED","ARCHIVED")) {
            jdbcTemplate.update("update pos_api_it.branches set status=? where id=?",status,w.branchId());
            var response=mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/auth/me")
                    .header("Authorization","Bearer "+admin).header("X-Restaurant-Id",w.restaurantId()).header("X-Branch-Id",w.branchId()))
                    .andReturn().getResponse();
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getHeader("X-POS-Context-Invalid")).isEqualTo("true");
        }
        jdbcTemplate.update("update pos_api_it.branches set status='ACTIVE', is_active=false where id=?",w.branchId());
        super.get("/auth/me").as(admin).header("X-Restaurant-Id",w.restaurantId().toString()).header("X-Branch-Id",w.branchId().toString()).expect(403);
        jdbcTemplate.update("update pos_api_it.branches set is_active=true where id=?",w.branchId());
        super.get("/auth/me").as(admin).header("X-Restaurant-Id",w.restaurantId().toString()).header("X-Branch-Id",w.branchId().toString()).expect(200);
        jdbcTemplate.update("update pos_api_it.branches set deleted_at=now() where id=?",w.branchId());
        super.get("/auth/me").as(admin).header("X-Restaurant-Id",w.restaurantId().toString()).header("X-Branch-Id",w.branchId().toString()).expect(403);
        super.get("/auth/me").as(admin).expect(200);
    }
    @Test void closedRestaurantRejectsOperationalContextButAllowsExplicitManagement() throws Exception {
        World w=newWorld("closed-navigation");String admin=token(superAdmin());
        for(String status:List.of("SUSPENDED","INACTIVE")) {
            jdbcTemplate.update("update pos_api_it.restaurants set status=?,is_active=false where id=?",status,w.restaurantId());
            super.get("/auth/me").as(admin).header("X-Restaurant-Id",w.restaurantId().toString()).header("X-Branch-Id",w.branchId().toString()).expect(403);
            super.get("/restaurants/{id}",w.restaurantId()).as(admin).header("X-Restaurant-Id",w.restaurantId().toString()).expect(200);
        }
        jdbcTemplate.update("update pos_api_it.restaurants set deleted_at=now() where id=?",w.restaurantId());
        super.get("/auth/me").as(admin).header("X-Restaurant-Id",w.restaurantId().toString()).expect(403);
    }
    @Test void branchCardsAndPagedApiIncludeMoreThanOneHundredBranches() throws Exception {
        World w=newWorld("many-branches");String admin=token(superAdmin());
        for(int i=0;i<120;i++) {
            Branch branch=new Branch();branch.setRestaurant(w.restaurant());branch.setName(String.format("Branch %03d",i));branch.setCode("B"+i);
            if(i%10==0){branch.setActive(false);branch.setStatus(BranchStatus.INACTIVE);}
            branchRepository.saveAndFlush(branch);
        }
        var cards=super.get("/restaurants/{id}/branches/cards",w.restaurantId()).as(admin).param("size",100).expect(200);
        assertThat(cards.get("items").size()).isEqualTo(100);assertThat(cards.get("totalElements").asLong()).isEqualTo(121);
        assertThat(cards.get("allBranches").asLong()).isEqualTo(121);assertThat(cards.get("openBranches").asLong()).isEqualTo(109);
        var rest=super.get("/restaurants/{id}/branches/cards",w.restaurantId()).as(admin).param("page",1).param("size",100).expect(200);
        assertThat(rest.get("items").size()).isEqualTo(21);assertThat(rest.get("hasNext").asBoolean()).isFalse();
        var first=super.get("/restaurants/{id}/branches",w.restaurantId()).as(admin).param("page",0).param("size",100).expect(200);
        var second=super.get("/restaurants/{id}/branches",w.restaurantId()).as(admin).param("page",1).param("size",100).expect(200);
        assertThat(first.get("items").size()).isEqualTo(100);assertThat(first.get("hasNext").asBoolean()).isTrue();
        assertThat(second.get("items").size()).isEqualTo(21);assertThat(second.get("hasNext").asBoolean()).isFalse();
    }
    @Test void cardSearchFilterEmptyAndExtremePageInputsStayPredictable() throws Exception {
        World w=newWorld("find-navigation");String admin=token(superAdmin());
        String unique="Navigation"+next();jdbcTemplate.update("update pos_api_it.restaurants set name=? where id=?",unique,w.restaurantId());
        var result=super.get("/restaurants/cards").as(admin).param("search",unique.toLowerCase()).param("filter","open").expect(200);
        assertThat(result.get("items").size()).isEqualTo(1);
        super.get("/restaurants/cards").as(admin).param("page",Integer.MAX_VALUE).param("size",100).expect(400);
        assertThat(super.get("/restaurants/cards").as(admin).param("search",unique).param("filter","closed").expect(200).get("items").size()).isZero();
        jdbcTemplate.update("update pos_api_it.restaurants set is_active=false,status='SUSPENDED' where id=?",w.restaurantId());
        assertThat(super.get("/restaurants/cards").as(admin).param("search",unique).param("filter","closed").expect(200).get("items").size()).isEqualTo(1);
        jdbcTemplate.update("update pos_api_it.restaurants set deleted_at=now() where id=?",w.restaurantId());
        assertThat(super.get("/restaurants/cards").as(admin).param("search",unique).expect(200).get("items").size()).isZero();
    }
    @Test void staffLoginRefreshAndRestorationDropAnUnavailableDefaultBranch() throws Exception {
        World w=newWorld("staff-navigation");
        jdbcTemplate.update("update pos_api_it.branches set status='INACTIVE',is_active=false where id=?",w.branchId());
        assertThat(super.get("/auth/me").as(w.ownerToken()).expect(200).path("defaultBranchId").isNull()).isTrue();
        var login=post("/auth/device/login").body(Map.of("identifier",w.owner().getUsername(),"password",PASSWORD)).expect(200);
        String refresh=login.get("refreshToken").asText();
        assertThat(login.path("user").path("defaultBranchId").isNull()).isTrue();
        var renewed=post("/auth/device/refresh").body(Map.of("refreshToken",refresh)).expect(200);
        assertThat(renewed.path("user").path("defaultBranchId").isNull()).isTrue();
        jdbcTemplate.update("update pos_api_it.branches set status='ACTIVE',is_active=true where id=?",w.branchId());
        assertThat(super.get("/auth/me").as(renewed.get("accessToken").asText()).expect(200).path("defaultBranchId").asText()).isEqualTo(w.branchId().toString());
        jdbcTemplate.update("update pos_api_it.branches set deleted_at=now() where id=?",w.branchId());
        assertThat(super.get("/auth/me").as(renewed.get("accessToken").asText()).expect(200).path("defaultBranchId").isNull()).isTrue();
    }

}
