package pos.pos.integration;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pos.pos.integration.support.AbstractPosApiIntegrationTest;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.repository.ReservationRepository;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Real PostgreSQL commits and HTTP authorization, including retry after an uncertain successful response. */
class RestaurantChangeSafetyIntegrationTest extends AbstractPosApiIntegrationTest {
 @Autowired ReservationRepository reservations;
 private JsonNode currencyPreview(World w,String currency,String rate,String step)throws Exception{
  return post("/restaurants/{r}/currency/preview",w.restaurantId()).as(superAdminToken())
   .body(Map.of("newCurrency",currency,"rate",rate,"roundingStep",step)).expect(200);
 }
 private Map<String,Object> currencyApply(JsonNode p,List<?> overrides){
  return Map.of("newCurrency",p.get("newCurrency").asText(),"rate",p.get("rate").asText(),"roundingStep",p.get("roundingStep").asText(),"previewToken",p.get("previewToken").asText(),"overrides",overrides);
 }
 private JsonNode zonePreview(World w,String zone)throws Exception{
  return post("/restaurants/{r}/timezone/preview",w.restaurantId()).as(superAdminToken()).body(Map.of("timezone",zone)).expect(200);
 }
 private Map<String,Object> zoneApply(JsonNode p,boolean clock,boolean confirmed){
  return Map.of("timezone",p.get("newTimezone").asText(),"keepBookingTimes",clock,"confirmed",confirmed,"previewToken",p.get("previewToken").asText());
 }
 private Reservation booking(World w,String start){
  var r=new Reservation();r.setRestaurant(w.restaurant());r.setBranch(w.branch());r.setReservationCode("TZ-"+UUID.randomUUID());
  r.setGuestToken(UUID.randomUUID().toString());r.setStatus(ReservationStatus.CONFIRMED);r.setContactName("Time test");
  r.setReservationStart(OffsetDateTime.parse(start));r.setReservationEnd(r.getReservationStart().plusHours(1));
  r.setHoldUntil(r.getReservationStart().plusMinutes(15));return reservations.saveAndFlush(r);
 }
 private String currency(World w){return jdbcTemplate.queryForObject("select currency from "+SCHEMA+".restaurants where id=?",String.class,w.restaurantId());}
 private String zone(World w){return jdbcTemplate.queryForObject("select timezone from "+SCHEMA+".restaurants where id=?",String.class,w.restaurantId());}
 @Test void currencyConvertsOnceAndExactRetryReturnsOriginalResult()throws Exception{
  var w=newWorld("currency-retry");var m=menu(w,"Dinner");var item=item(w,m,"Soup","10.15");var p=currencyPreview(w,"USD","1.5","0.05");var body=currencyApply(p,List.of());
  long initialVersion=jdbcTemplate.queryForObject("select version from "+SCHEMA+".restaurants where id=?",Long.class,w.restaurantId());
  var first=post("/restaurants/{r}/currency/change",w.restaurantId()).as(superAdminToken()).body(body).expect(200);
  var repeat=post("/restaurants/{r}/currency/change",w.restaurantId()).as(superAdminToken()).body(body).expect(200);
  assertThat(repeat).isEqualTo(first);assertThat(currency(w)).isEqualTo("USD");
  assertThat(jdbcTemplate.queryForObject("select version from "+SCHEMA+".restaurants where id=?",Long.class,w.restaurantId())).isEqualTo(initialVersion+1);
  assertThat(jdbcTemplate.queryForObject("select base_price from "+SCHEMA+".\"menu-items\" where id=?",java.math.BigDecimal.class,item)).isEqualByComparingTo("15.25");
  assertThat(jdbcTemplate.queryForObject("select count(*) from "+SCHEMA+".restaurant_currency_changes where restaurant_id=?",Integer.class,w.restaurantId())).isEqualTo(1);
 }
 @Test void currencyRejectsMissingAndStalePreviewWithoutChangingAnyPrice()throws Exception{
  var w=newWorld("currency-stale");var m=menu(w,"Dinner");var item=item(w,m,"Soup","10");var p=currencyPreview(w,"USD","2","0.01");
  var missing=new HashMap<>(currencyApply(p,List.of()));missing.remove("previewToken");
  post("/restaurants/{r}/currency/change",w.restaurantId()).as(superAdminToken()).body(missing).expect(409);
  jdbcTemplate.update("update "+SCHEMA+".\"menu-items\" set base_price=11 where id=?",item);
  post("/restaurants/{r}/currency/change",w.restaurantId()).as(superAdminToken()).body(currencyApply(p,List.of())).expect(409);
  assertThat(currency(w)).isEqualTo("EUR");
 }
 @Test void currencyRejectsChangedChoicesAndDuplicateCorrections()throws Exception{
  var w=newWorld("currency-choices");var m=menu(w,"Dinner");var item=item(w,m,"Soup","10");var p=currencyPreview(w,"USD","2","0.01");
  var changed=new HashMap<>(currencyApply(p,List.of()));changed.put("rate","3");
  post("/restaurants/{r}/currency/change",w.restaurantId()).as(superAdminToken()).body(changed).expect(409);
  var correction=Map.of("kind","DISH_PRICE","id",item.toString(),"value","25");
  post("/restaurants/{r}/currency/change",w.restaurantId()).as(superAdminToken()).body(currencyApply(p,List.of(correction,correction))).expect(400);
  assertThat(currency(w)).isEqualTo("EUR");
 }
 @Test void currencyRejectsUnknownCorrectionAndOverflowAtomically()throws Exception{
  var w=newWorld("currency-overflow");var m=menu(w,"Dinner");var item=item(w,m,"Soup","10");var p=currencyPreview(w,"USD","2","0.01");
  post("/restaurants/{r}/currency/change",w.restaurantId()).as(superAdminToken()).body(currencyApply(p,List.of(Map.of("kind","DISH_PRICE","id",UUID.randomUUID().toString(),"value","2")))).expect(409);
  post("/restaurants/{r}/currency/change",w.restaurantId()).as(superAdminToken()).body(currencyApply(p,List.of(Map.of("kind","DISH_PRICE","id",item.toString(),"value","-1")))).expect(400);
  assertThat(currency(w)).isEqualTo("EUR");
 }
 @Test void correctedIngredientCostRecalculatesRecipeInsteadOfMultiplyingOldCost()throws Exception{
  var w=newWorld("recipe-currency");String admin=superAdminToken(),root="/restaurants/"+w.restaurantId();
  var stock=post(root+"/inventory/items").as(admin).body(Map.of("name","Flour","itemType","INGREDIENT","baseUnit","KILOGRAM","costPerUnit","2")).expect(201);
  var recipe=post(root+"/recipes").as(admin).body(Map.of("name","Dough","recipeType","PREP_BATCH","yieldQuantity","1","yieldUnit","KILOGRAM")).expect(201);
  put(root+"/recipes/"+recipe.get("id").asText()+"/components").as(admin).body(Map.of("componentType","INVENTORY_ITEM","inventoryItemId",stock.get("id").asText(),"quantity","2","unit","KILOGRAM")).expect(200);
  var p=currencyPreview(w,"USD","2","0.01");post(root+"/currency/change").as(admin).body(currencyApply(p,List.of(Map.of("kind","STOCK_COST","id",stock.get("id").asText(),"value","3")))).expect(200);
  var updated=get(root+"/recipes/"+recipe.get("id").asText()).as(admin).expect(200);
  assertThat(money(updated,"theoreticalCost")).isEqualByComparingTo("6");
 }
 @Test void overflowCorrectionIsRejectedAndEveryPriceRemainsUnchanged()throws Exception{
  var w=newWorld("true-overflow");var m=menu(w,"Dinner");var i=item(w,m,"Soup","10");var p=currencyPreview(w,"USD","2","0.01");
  post("/restaurants/{r}/currency/change",w.restaurantId()).as(superAdminToken()).body(currencyApply(p,List.of(Map.of("kind","DISH_PRICE","id",i.toString(),"value","100000000000000000000")))).expect(400);
  assertThat(currency(w)).isEqualTo("EUR");assertThat(jdbcTemplate.queryForObject("select base_price from "+SCHEMA+".\"menu-items\" where id=?",java.math.BigDecimal.class,i)).isEqualByComparingTo("10");
 }
 @Test void openOrdersOpenShiftsAndBookingDepositsBlockCurrencyChange()throws Exception{
  for(String condition:List.of("order","shift","deposit")){
   var w=newWorld("money-"+condition);var p=currencyPreview(w,"USD","2","0.01");
   if(condition.equals("order")){var m=menu(w,"Dinner");order(w,w.ownerToken(),Map.of(item(w,m,"Soup","10"),1));}
   else if(condition.equals("shift"))post("/restaurants/{r}/branches/{b}/shifts/clock-in",w.restaurantId(),w.branchId()).as(w.ownerToken()).body(Map.of()).expect(200);
   else{var r=booking(w,"2030-02-01T20:00:00Z");jdbcTemplate.update("update "+SCHEMA+".reservations set deposit_required=true,deposit_amount=10,deposit_status='PAID' where id=?",r.getId());}
   post("/restaurants/{r}/currency/change",w.restaurantId()).as(superAdminToken()).body(currencyApply(p,List.of())).expect(409);assertThat(currency(w)).isEqualTo("EUR");
  }
 }
 @Test void movingBookingIntoPastRollsBackTimezoneAndBooking()throws Exception{
  var w=newWorld("zone-past","EUR","UTC");var r=booking(w,OffsetDateTime.now(ZoneOffset.UTC).withNano(0).plusHours(1).toString());var p=zonePreview(w,"Pacific/Kiritimati");
  post("/restaurants/{r}/timezone/change",w.restaurantId()).as(superAdminToken()).body(zoneApply(p,true,true)).expect(409);
  assertThat(zone(w)).isEqualTo("UTC");assertThat(reservations.findById(r.getId()).orElseThrow().getReservationStart().toInstant()).isEqualTo(r.getReservationStart().toInstant());
 }
 @Test void currencyUsesWholeYenAndRejectsUnsupportedCurrenciesAndSteps()throws Exception{
  var w=newWorld("yen");var m=menu(w,"Dinner");item(w,m,"Soup","10.25");
  for(var input:List.of(Map.of("newCurrency","JPY","rate","1","roundingStep","0.01"),Map.of("newCurrency","KWD","rate","1","roundingStep","0.01")))
   post("/restaurants/{r}/currency/preview",w.restaurantId()).as(superAdminToken()).body(input).expect(400);
  var p=currencyPreview(w,"JPY","150","1");
  post("/restaurants/{r}/currency/change",w.restaurantId()).as(superAdminToken()).body(currencyApply(p,List.of())).expect(200);
  assertThat(currency(w)).isEqualTo("JPY");
 }
 @Test void timezoneKeepsLocalClockMovesHoldAndReplaysOnce()throws Exception{
  var w=newWorld("zone-clock","EUR","UTC");var r=booking(w,"2030-02-01T20:00:00Z");var p=zonePreview(w,"Europe/Berlin");var body=zoneApply(p,true,true);
  var first=post("/restaurants/{r}/timezone/change",w.restaurantId()).as(superAdminToken()).body(body).expect(200);
  assertThat(post("/restaurants/{r}/timezone/change",w.restaurantId()).as(superAdminToken()).body(body).expect(200)).isEqualTo(first);
  var moved=reservations.findById(r.getId()).orElseThrow();
  assertThat(moved.getReservationStart().toInstant()).isEqualTo(Instant.parse("2030-02-01T19:00:00Z"));
  assertThat(moved.getHoldUntil().toInstant()).isEqualTo(Instant.parse("2030-02-01T19:15:00Z"));assertThat(moved.getScheduleChangedAt()).isNotNull();
  assertThat(jdbcTemplate.queryForObject("select count(*) from "+SCHEMA+".restaurant_timezone_changes where restaurant_id=?",Integer.class,w.restaurantId())).isEqualTo(1);
 }
 @Test void timezoneKeepsExactMomentWhenRequested()throws Exception{
  var w=newWorld("zone-instant","EUR","UTC");var r=booking(w,"2030-02-01T20:00:00Z");var p=zonePreview(w,"Asia/Kathmandu");
  post("/restaurants/{r}/timezone/change",w.restaurantId()).as(superAdminToken()).body(zoneApply(p,false,true)).expect(200);
  assertThat(reservations.findById(r.getId()).orElseThrow().getReservationStart().toInstant()).isEqualTo(r.getReservationStart().toInstant());
 }
 @Test void timezoneRefusesMissingAndRepeatedHoursAndRollsBack()throws Exception{
  for(String start:List.of("2030-03-31T02:30:00Z","2030-10-27T02:30:00Z")){
   var w=newWorld("zone-dst","EUR","UTC");var r=booking(w,start);var p=zonePreview(w,"Europe/Berlin");
   post("/restaurants/{r}/timezone/change",w.restaurantId()).as(superAdminToken()).body(zoneApply(p,true,true)).expect(409);
   assertThat(zone(w)).isEqualTo("UTC");assertThat(reservations.findById(r.getId()).orElseThrow().getReservationStart().toInstant()).isEqualTo(r.getReservationStart().toInstant());
  }
 }
 @Test void timezoneRequiresConfirmationAndDetectsBookingEditedSincePreview()throws Exception{
  var w=newWorld("zone-stale","EUR","UTC");var r=booking(w,"2030-02-01T20:00:00Z");var p=zonePreview(w,"Europe/Berlin");
  post("/restaurants/{r}/timezone/change",w.restaurantId()).as(superAdminToken()).body(zoneApply(p,true,false)).expect(409);
  r.setReservationStart(r.getReservationStart().plusMinutes(10));reservations.saveAndFlush(r);
  post("/restaurants/{r}/timezone/change",w.restaurantId()).as(superAdminToken()).body(zoneApply(p,true,true)).expect(409);assertThat(zone(w)).isEqualTo("UTC");
 }
 @Test void timezoneRefusesBookingOutsideSpecialHours()throws Exception{
  var w=newWorld("zone-hours","EUR","UTC");booking(w,"2030-02-01T20:00:00Z");
  jdbcTemplate.update("insert into "+SCHEMA+".\"settings-special-hours\" (id,branch_id,special_date,is_closed,open_time,close_time,created_at,updated_at) values (?,?,date '2030-02-01',false,time '09:00',time '18:00',now(),now())",UUID.randomUUID(),w.branchId());
  var p=zonePreview(w,"Europe/Berlin");post("/restaurants/{r}/timezone/change",w.restaurantId()).as(superAdminToken()).body(zoneApply(p,true,true)).expect(409);assertThat(zone(w)).isEqualTo("UTC");
 }
}
