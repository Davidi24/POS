package pos.pos.integration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pos.pos.integration.support.AbstractPosApiIntegrationTest;
import pos.pos.restaurant.entity.Branch;
import pos.pos.menu.image.StoredImageCleanup;
import pos.pos.menu.image.MenuImageStorage;
import pos.pos.inventory.entity.InventoryLocation;
import pos.pos.inventory.repository.InventoryLocationRepository;
import pos.pos.kds.entity.KdsStation;
import pos.pos.kds.repository.KdsStationRepository;
import pos.pos.tables.entity.RestaurantTable;
import pos.pos.tables.repository.RestaurantTableRepository;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
class RestaurantClosureAndReceiptIntegrationTest extends AbstractPosApiIntegrationTest {
 @Autowired StoredImageCleanup cleanup;
 @Autowired InventoryLocationRepository locations;
 @Autowired KdsStationRepository stations;
 @Autowired RestaurantTableRepository tables;
 private Branch branch(World w,String name){var b=new Branch();b.setRestaurant(w.restaurant());b.setName(name);b.setCode("B"+next());return branchRepository.saveAndFlush(b);}
 @Test void closedBranchRejectsNewOrdersAndClockInUntilReopened()throws Exception{
  var w=newWorld("closed-work");var b=branch(w,"Secondary");var admin=superAdminToken();
  String root="/restaurants/"+w.restaurantId()+"/branches/"+b.getId();
  patch(root+"/status").as(admin).body(Map.of("isActive",false,"status","TEMPORARILY_CLOSED")).expect(200);
  post(root+"/orders").as(admin).body(Map.of("orderType","TAKEAWAY","guestCount",1,"items",List.of())).expect(409);
  post(root+"/shifts/clock-in").as(w.ownerToken()).body(Map.of()).expect(409);
  var start=java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusDays(1).withNano(0);
  var booking=Map.of("branchId",b.getId(),"partySize",2,"contactName","Closed test","reservationStart",start.toString(),"reservationEnd",start.plusHours(1).toString());
  post("/restaurants/"+w.restaurantId()+"/reservations").as(admin).body(booking).expect(409);
  get(root).as(admin).expect(200);
  patch(root+"/status").as(admin).body(Map.of("isActive",true,"status","ACTIVE")).expect(200);
  post(root+"/orders").as(admin).body(Map.of("orderType","TAKEAWAY","guestCount",1,"items",List.of())).expect(201);
  post(root+"/shifts/clock-in").as(w.ownerToken()).body(Map.of()).expect(200);
 }
 @Test void cannotCloseOrArchiveWithOpenOrder()throws Exception{
  var w=newWorld("close-open");var m=menu(w,"Dinner");var i=item(w,m,"Soup","10");order(w,w.ownerToken(),Map.of(i,1));
  patch("/restaurants/{r}/status",w.restaurantId()).as(superAdminToken()).body(Map.of("isActive",false,"status","INACTIVE")).expect(409);
  delete("/restaurants/{r}",w.restaurantId()).as(superAdminToken()).expect(409);
  assertThat(restaurantRepository.findById(w.restaurantId()).orElseThrow().getDeletedAt()).isNull();
 }
 @Test void lastUsableBranchRequiresClosingRestaurantFirst()throws Exception{
  var w=newWorld("last-open");var closed=branch(w,"Closed");jdbcTemplate.update("update "+SCHEMA+".branches set is_active=false,status='TEMPORARILY_CLOSED' where id=?",closed.getId());
  patch("/restaurants/{r}/branches/{b}/status",w.restaurantId(),w.branchId()).as(superAdminToken()).body(Map.of("isActive",false,"status","TEMPORARILY_CLOSED")).expect(409);
  patch("/restaurants/{r}/status",w.restaurantId()).as(superAdminToken()).body(Map.of("isActive",false,"status","INACTIVE")).expect(200);
  patch("/restaurants/{r}/branches/{b}/status",w.restaurantId(),w.branchId()).as(superAdminToken()).body(Map.of("isActive",false,"status","TEMPORARILY_CLOSED")).expect(200);
  patch("/restaurants/{r}/status",w.restaurantId()).as(superAdminToken()).body(Map.of("isActive",true,"status","ACTIVE")).expect(409);
  patch("/restaurants/{r}/branches/{b}/status",w.restaurantId(),w.branchId()).as(superAdminToken()).body(Map.of("isActive",true,"status","ACTIVE")).expect(200);
  patch("/restaurants/{r}/status",w.restaurantId()).as(superAdminToken()).body(Map.of("isActive",true,"status","ACTIVE")).expect(200);
 }
 @Test void removalListsTablesStockAndKitchenAndAllowsRemovalAfterTheyAreRetired()throws Exception{
  var w=newWorld("branch-dependencies");var b=branch(w,"Kitchen branch");
  var table=new RestaurantTable();table.setRestaurant(w.restaurant());table.setBranch(b);table.setTableNumber("T1");table.setName("Table");table.setCapacity(2);table.setFloor("Main");table.setPositionX(java.math.BigDecimal.ZERO);table.setPositionY(java.math.BigDecimal.ZERO);tables.saveAndFlush(table);
  var location=new InventoryLocation();location.setRestaurant(w.restaurant());location.setBranch(b);location.setName("Store");location.setCode("STORE"+next());locations.saveAndFlush(location);
  var station=new KdsStation();station.setRestaurant(w.restaurant());station.setBranch(b);station.setName("Prep");station.setCode("PREP");station.setStationType(pos.pos.kds.enums.KdsStationType.PREP);stations.saveAndFlush(station);
  var check=get("/restaurants/{r}/branches/{b}/removal-check",w.restaurantId(),b.getId()).as(superAdminToken()).expect(200);
  assertThat(check.get("canRemove").asBoolean()).isFalse();assertThat(check.get("reasons").toString()).contains("tables","stock","kitchen");
  delete("/restaurants/{r}/branches/{b}",w.restaurantId(),b.getId()).as(superAdminToken()).expect(409);
  jdbcTemplate.update("update "+SCHEMA+".tables set is_active=false where id=?",table.getId());
  jdbcTemplate.update("update "+SCHEMA+".inventory_locations set is_active=false where id=?",location.getId());
  jdbcTemplate.update("update "+SCHEMA+".kds_stations set is_active=false where id=?",station.getId());
  delete("/restaurants/{r}/branches/{b}",w.restaurantId(),b.getId()).as(superAdminToken()).expect(204);
  assertThat(branchRepository.findById(b.getId()).orElseThrow().getDeletedAt()).isNotNull();assertThat(tables.findById(table.getId())).isPresent();
 }
 @Test void concurrentRemovalsCannotRemoveBothRemainingActiveBranches()throws Exception{
  var w=newWorld("branch-race");var a=branch(w,"A");var b=branch(w,"B");var token=superAdminToken();
  jdbcTemplate.update("update "+SCHEMA+".branches set is_active=false,status='TEMPORARILY_CLOSED' where id=?",w.branchId());
  try(var pool=Executors.newFixedThreadPool(2)){
   var start=new CountDownLatch(1);var f1=pool.submit(()->{start.await();return delete("/restaurants/{r}/branches/{b}",w.restaurantId(),a.getId()).as(token).send().status();});
   var f2=pool.submit(()->{start.await();return delete("/restaurants/{r}/branches/{b}",w.restaurantId(),b.getId()).as(token).send().status();});start.countDown();
   assertThat(List.of(f1.get(20,TimeUnit.SECONDS),f2.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(204,409);
  }
 }
 @Test void paidReceiptKeepsIdentityTaxAddressAndLogoAcrossEditsAndArchive()throws Exception{
  var w=newWorld("receipt-history");var m=menu(w,"Dinner");var i=item(w,m,"Soup","10");var o=id(order(w,w.ownerToken(),Map.of(i,1)));
  String logo=MenuImageStorage.backendUrl(MenuImageStorage.newKey(w.restaurantId(),".png"));
  jdbcTemplate.update("insert into "+SCHEMA+".\"restaurant-branding\" (id,restaurant_id,logo_url,created_at,updated_at) values (?,?,?,now(),now())",UUID.randomUUID(),w.restaurantId(),logo);
  jdbcTemplate.update("insert into "+SCHEMA+".\"restaurant-tax-profiles\" (id,restaurant_id,country,vat_number,is_default,effective_from,effective_to,created_at,updated_at) values (?,?,'AL','OLD-VAT',true,now()-interval '1 day',now()+interval '1 day',now(),now())",UUID.randomUUID(),w.restaurantId());
  post("/restaurants/{r}/branches/{b}/addresses",w.restaurantId(),w.branchId()).as(superAdminToken()).body(Map.of("addressType","PHYSICAL","streetLine1","Old street 1","city","Tirane","country","AL","isPrimary",true)).expect(201);
  get("/restaurants/{r}/settings",w.restaurantId()).as(w.ownerToken()).expect(200);
  assertThat(jdbcTemplate.update("update "+SCHEMA+".\"settings-receipts\" set show_logo=true where settings_id=(select id from "+SCHEMA+".settings where restaurant_id=?)",w.restaurantId())).isEqualTo(1);
  post("/restaurants/{r}/orders/{o}/payments",w.restaurantId(),o).as(w.ownerToken()).body(Map.of("method","CASH","amount","10","tenderedAmount","10")).expect(201);
  var before=get("/restaurants/{r}/orders/{o}/receipt",w.restaurantId(),o).as(superAdminToken()).expect(200);
  assertThat(before.get("vatNumber").asText()).isEqualTo("OLD-VAT");assertThat(before.get("logoUrl").asText()).isEqualTo(logo);
  jdbcTemplate.update("update "+SCHEMA+".restaurants set name='New name',legal_name='New legal' where id=?",w.restaurantId());
  jdbcTemplate.update("update "+SCHEMA+".\"branch-addresses\" set street_line_1='New street' where branch_id=?",w.branchId());
  jdbcTemplate.update("update "+SCHEMA+".\"restaurant-tax-profiles\" set vat_number='NEW-VAT' where restaurant_id=?",w.restaurantId());
  jdbcTemplate.update("update "+SCHEMA+".\"restaurant-branding\" set logo_url=null where restaurant_id=?",w.restaurantId());
  assertThat(cleanup.references(logo)).isEqualTo(1);
  delete("/restaurants/{r}",w.restaurantId()).as(superAdminToken()).expect(204);
  var after=get("/restaurants/{r}/orders/{o}/receipt",w.restaurantId(),o).as(superAdminToken()).expect(200);
  for(String field:List.of("restaurantName","legalName","branchName","addressLines","vatNumber","taxNumber","phone","logoUrl","total","currency"))assertThat(after.get(field)).as(field).isEqualTo(before.get(field));
  var other=newWorld("receipt-other");get("/restaurants/{r}/orders/{o}/receipt",w.restaurantId(),o).as(other.ownerToken()).expect(403);
 }
 @Test void expiredTaxIdentityIsNotPrintedOnNewReceipt()throws Exception{
  var w=newWorld("tax-expired");var m=menu(w,"Dinner");var i=item(w,m,"Soup","10");var o=id(order(w,w.ownerToken(),Map.of(i,1)));
  jdbcTemplate.update("insert into "+SCHEMA+".\"restaurant-tax-profiles\" (id,restaurant_id,country,vat_number,is_default,effective_from,effective_to,created_at,updated_at) values (?,?,'AL','EXPIRED-VAT',true,now()-interval '2 days',now()-interval '1 day',now(),now())",UUID.randomUUID(),w.restaurantId());
  post("/restaurants/{r}/orders/{o}/payments",w.restaurantId(),o).as(w.ownerToken()).body(Map.of("method","CASH","amount","10","tenderedAmount","10")).expect(201);
  var receipt=get("/restaurants/{r}/orders/{o}/receipt",w.restaurantId(),o).as(superAdminToken()).expect(200);
  assertThat(receipt.get("vatNumber").isNull()).isTrue();
 }
}
