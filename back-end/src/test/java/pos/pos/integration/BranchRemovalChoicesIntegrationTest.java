package pos.pos.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pos.pos.integration.support.AbstractPosApiIntegrationTest;
import pos.pos.restaurant.entity.Branch;
import pos.pos.inventory.entity.*;
import pos.pos.inventory.repository.*;
import pos.pos.kds.entity.KdsStation;
import pos.pos.kds.repository.KdsStationRepository;
import pos.pos.tables.entity.RestaurantTable;
import pos.pos.tables.repository.RestaurantTableRepository;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

class BranchRemovalChoicesIntegrationTest extends AbstractPosApiIntegrationTest {
    @Autowired InventoryLocationRepository locations;
    @Autowired InventoryItemRepository stockItems;
    @Autowired InventoryLevelRepository levels;
    @Autowired KdsStationRepository stations;
    @Autowired RestaurantTableRepository tables;
    private Branch branch(World w,String name) {
        var b=new Branch();b.setRestaurant(w.restaurant());b.setName(name);b.setCode("B"+next());return branchRepository.saveAndFlush(b);
    }
    private String path(World w,Branch b) {return "/restaurants/"+w.restaurantId()+"/branches/"+b.getId();}
    private JsonNode plan(World w,Branch b,String token) throws Exception {return get(path(w,b)+"/removal-check").as(token).expect(200).get("archive");}
    private Map<String,Object> request(Branch b,JsonNode plan,String action) {
        return new HashMap<>(Map.of("staffAction",action,"expectedVersion",plan.get("expectedVersion").asLong(),"confirmedName",b.getName(),"previewToken",plan.get("previewToken").asText()));
    }
    private InventoryLocation location(World w,Branch b) {
        var l=new InventoryLocation();l.setRestaurant(w.restaurant());l.setBranch(b);l.setName("Store");l.setCode("L"+next());return locations.saveAndFlush(l);
    }
    @Test void keepStaffRetiresSetupClearsAssignmentsAndPreservesHistory() throws Exception {
        var w=newWorld("remove-keep");branch(w,"Remaining");var admin=superAdminToken();
        var waiter=staff(w,"waiter","WAITER");String waiterToken=token(waiter);
        var table=new RestaurantTable();table.setRestaurant(w.restaurant());table.setBranch(w.branch());table.setTableNumber("T1");table.setCapacity(2);table.setFloor("Main");table.setPositionX(BigDecimal.ZERO);table.setPositionY(BigDecimal.ZERO);tables.saveAndFlush(table);
        var loc=location(w,w.branch());
        var station=new KdsStation();station.setRestaurant(w.restaurant());station.setBranch(w.branch());station.setCode("PREP");station.setName("Prep");station.setStationType(pos.pos.kds.enums.KdsStationType.PREP);stations.saveAndFlush(station);
        UUID device=UUID.randomUUID();jdbcTemplate.update("insert into pos_api_it.devices(id,restaurant_id,branch_id,code,name,device_type,status,is_active,is_online,created_at,updated_at) values (?,?,?,'POS','Till','POS_TERMINAL','ACTIVE',true,true,now(),now())",device,w.restaurantId(),w.branchId());
        UUID assignment=UUID.randomUUID();jdbcTemplate.update("insert into pos_api_it.\"device-assignments\"(id,device_id,user_id,assignment_type,assigned_at,is_active,created_at,updated_at) values (?,?,?,'USER',now(),true,now(),now())",assignment,device,waiter.getId());
        var menu=menu(w,"Dinner");var dish=item(w,menu,"Soup","10");var order=id(order(w,w.ownerToken(),Map.of(dish,1)));
        post("/restaurants/{r}/orders/{o}/payments",w.restaurantId(),order).as(w.ownerToken()).body(Map.of("method","CASH","amount","10","tenderedAmount","10")).expect(201);
        var receipt=get("/restaurants/{r}/orders/{o}/receipt",w.restaurantId(),order).as(admin).expect(200);
        var preview=plan(w,w.branch(),admin);assertThat(preview.path("canRemove").asBoolean()).isTrue();assertThat(preview.path("staff").asInt()).isEqualTo(2);assertThat(preview.path("protectedStaff").asInt()).isEqualTo(1);
        for(String key:List.of("devices","tables","stockLocations","kitchenStations")) assertThat(preview.path(key).asInt()).as(key).isEqualTo(1);
        delete(path(w,w.branch())).as(admin).body(request(w.branch(),preview,"KEEP")).expect(204);
        var saved=userRepository.findById(waiter.getId()).orElseThrow();assertThat(saved.isActive()).isTrue();assertThat(saved.getDefaultBranchId()).isNull();assertThat(saved.getDeletedAt()).isNull();
        get("/auth/me").as(waiterToken).expect(200);
        assertThat(branchRepository.findById(w.branchId()).orElseThrow().getDeletedAt()).isNotNull();
        assertThat(tables.findById(table.getId()).orElseThrow().isActive()).isFalse();assertThat(locations.findById(loc.getId()).orElseThrow().isActive()).isFalse();assertThat(stations.findById(station.getId()).orElseThrow().isActive()).isFalse();
        assertThat(jdbcTemplate.queryForObject("select status from pos_api_it.devices where id=?",String.class,device)).isEqualTo("RETIRED");
        var afterReceipt=get("/restaurants/{r}/orders/{o}/receipt",w.restaurantId(),order).as(admin).expect(200);
        ((com.fasterxml.jackson.databind.node.ObjectNode)receipt).remove("printedAt");
        ((com.fasterxml.jackson.databind.node.ObjectNode)afterReceipt).remove("printedAt");
        assertThat(afterReceipt).isEqualTo(receipt);
        assertThat(jdbcTemplate.queryForObject("select is_active from pos_api_it.\"device-assignments\" where id=?",Boolean.class,assignment)).isFalse();
        assertThat(jdbcTemplate.queryForObject("select message from pos_api_it.\"settings-audit-logs\" where entity_id=? and action='ARCHIVE'",String.class,w.branchId())).contains("staff choice=KEEP");
        assertThat(get("/restaurants/{r}/branches/cards",w.restaurantId()).as(admin).expect(200).toString()).doesNotContain(w.branchId().toString());
        assertThat(jdbcTemplate.queryForObject("select count(*) from pos_api_it.menus where id=?",Long.class,menu.get("menuId"))).isEqualTo(1);
    }
    @Test void deactivateStaffRevokesExistingSessionsButKeepsOwnersAndOtherBranches() throws Exception {
        var w=newWorld("remove-deactivate");var b=branch(w,"Remove");var admin=superAdminToken();
        var target=staff(w.restaurant(),b,"target","WAITER");String access=token(target);
        var coOwner=staff(w.restaurant(),b,"protected","CO_OWNER");var elsewhere=staff(w,"elsewhere","WAITER");
        var preview=plan(w,b,admin);assertThat(preview.get("protectedStaff").asInt()).isEqualTo(1);
        delete(path(w,b)).as(admin).body(request(b,preview,"DEACTIVATE")).expect(204);
        var inactive=userRepository.findById(target.getId()).orElseThrow();assertThat(inactive.isActive()).isFalse();assertThat(inactive.getStatus()).isEqualTo("INACTIVE");assertThat(inactive.getDeletedAt()).isNull();assertThat(inactive.getDefaultBranchId()).isNull();
        get("/auth/me").as(access).expect(401);
        assertThat(userRepository.findById(coOwner.getId()).orElseThrow().isActive()).isTrue();assertThat(userRepository.findById(coOwner.getId()).orElseThrow().getDefaultBranchId()).isNull();
        assertThat(userRepository.findById(elsewhere.getId()).orElseThrow().getDefaultBranchId()).isEqualTo(w.branchId());
    }
    @Test void staleStaffPreviewCannotDeactivateAnUnreviewedNewPerson() throws Exception {
        var w=newWorld("stale-removal");var b=branch(w,"Remove");var admin=superAdminToken();var preview=plan(w,b,admin);
        var joined=staff(w.restaurant(),b,"new","WAITER");
        delete(path(w,b)).as(admin).body(request(b,preview,"DEACTIVATE")).expect(409);
        assertThat(userRepository.findById(joined.getId()).orElseThrow().isActive()).isTrue();assertThat(branchRepository.findById(b.getId()).orElseThrow().getDeletedAt()).isNull();
        delete(path(w,b)).as(admin).body(request(b,plan(w,b,admin),"KEEP")).expect(204);
    }
    @Test void validationAndPermissionsCannotBypassReviewedChoice() throws Exception {
        var w=newWorld("removal-permissions");var foreign=newWorld("foreign");var b=branch(w,"Remove");var admin=superAdminToken();
        var target=staff(w.restaurant(),b,"target","WAITER");var co=staff(w,"co","CO_SUPER_ADMIN");String restricted=token(co);
        put("/co-super-admins/{id}/permissions",co.getId()).as(admin).body(Map.of("grantedCodes",List.of("RESTAURANTS_READ","RESTAURANTS_UPDATE"))).expect(200);
        var preview=plan(w,b,restricted);assertThat(preview.get("canDeactivateStaff").asBoolean()).isFalse();
        var body=request(b,preview,"DEACTIVATE");delete(path(w,b)).as(restricted).body(body).expect(403);
        delete(path(w,b)).as(w.ownerToken()).body(body).expect(403);delete(path(w,b)).as(foreign.ownerToken()).body(body).expect(403);
        for(String key:List.of("staffAction","expectedVersion","confirmedName","previewToken")) {var invalid=new HashMap<>(body);invalid.remove(key);delete(path(w,b)).as(admin).body(invalid).expect(400);}
        body.put("staffAction","ERASE");delete(path(w,b)).as(admin).body(body).expect(400);
        body.put("staffAction","KEEP");body.put("confirmedName","Wrong");delete(path(w,b)).as(admin).body(body).expect(400);
        assertThat(userRepository.findById(target.getId()).orElseThrow().isActive()).isTrue();
        delete(path(w,b)).as(restricted).body(request(b,plan(w,b,restricted),"KEEP")).expect(204);
    }
    @Test void remainingOrCommittedStockBlocksAllChanges() throws Exception {
        var w=newWorld("stock-removal");var b=branch(w,"Remove");var admin=superAdminToken();var target=staff(w.restaurant(),b,"target","WAITER");var loc=location(w,b);
        var item=new InventoryItem();item.setRestaurant(w.restaurant());item.setCode("I"+next());item.setName("Stock");stockItems.saveAndFlush(item);
        var level=new InventoryLevel();level.setLocation(loc);level.setInventoryItem(item);level.setOnHandQuantity(BigDecimal.ONE);levels.saveAndFlush(level);
        for(String column:List.of("on_hand_quantity","committed_quantity")) {
            jdbcTemplate.update("update pos_api_it.inventory_levels set on_hand_quantity=0,committed_quantity=0 where id=?",level.getId());
            jdbcTemplate.update("update pos_api_it.inventory_levels set "+column+"=1 where id=?",level.getId());
            var preview=plan(w,b,admin);assertThat(preview.get("canRemove").asBoolean()).isFalse();
            delete(path(w,b)).as(admin).body(request(b,preview,"DEACTIVATE")).expect(409);
            assertThat(userRepository.findById(target.getId()).orElseThrow().isActive()).isTrue();assertThat(locations.findById(loc.getId()).orElseThrow().isActive()).isTrue();
        }
    }
    @Test void unfinishedWorkAndLastBranchStillBlockRemoval() throws Exception {
        var w=newWorld("busy-removal");var admin=superAdminToken();var last=plan(w,w.branch(),admin);assertThat(last.get("canRemove").asBoolean()).isFalse();
        delete(path(w,w.branch())).as(admin).body(request(w.branch(),last,"KEEP")).expect(409);
        branch(w,"Remaining");var m=menu(w,"Dinner");var i=item(w,m,"Soup","10");order(w,w.ownerToken(),Map.of(i,1));
        var preview=plan(w,w.branch(),admin);assertThat(preview.get("reasons").toString()).contains("open orders");
        delete(path(w,w.branch())).as(admin).body(request(w.branch(),preview,"DEACTIVATE")).expect(409);
        assertThat(userRepository.findById(w.owner().getId()).orElseThrow().getDefaultBranchId()).isEqualTo(w.branchId());
    }
    @Test void twoConcurrentRemovalsCannotRemoveTheLastUsableBranch() throws Exception {
        var w=newWorld("removal-race");var a=branch(w,"A");var b=branch(w,"B");var admin=superAdminToken();
        jdbcTemplate.update("update pos_api_it.branches set is_active=false,status='INACTIVE' where id=?",w.branchId());
        var ra=request(a,plan(w,a,admin),"KEEP");var rb=request(b,plan(w,b,admin),"KEEP");
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);var fa=pool.submit(()->{start.await();return delete(path(w,a)).as(admin).body(ra).send().status();});
            var fb=pool.submit(()->{start.await();return delete(path(w,b)).as(admin).body(rb).send().status();});start.countDown();
            assertThat(List.of(fa.get(25,TimeUnit.SECONDS),fb.get(25,TimeUnit.SECONDS))).containsExactlyInAnyOrder(204,409);
        }
    }
}
