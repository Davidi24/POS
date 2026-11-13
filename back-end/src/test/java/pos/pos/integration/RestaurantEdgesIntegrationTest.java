package pos.pos.integration;
import org.junit.jupiter.api.Test;
import pos.pos.integration.support.AbstractPosApiIntegrationTest;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
class RestaurantEdgesIntegrationTest extends AbstractPosApiIntegrationTest {
 private Map<String,Object> creation(String code){return Map.of("name","Edge "+code,"legalName","Edge LLC","code",code,"slug",code.toLowerCase(),"currency","EUR","timezone","Europe/Berlin","email","contact@example.invalid","phone","+355691112222");}
 private Map<String,Object> address(boolean primary){return Map.of("addressType","PHYSICAL","country","日本","city","東京","streetLine1","Straße 10 – 日本橋","isPrimary",primary);}
 private Map<String,Object> contact(boolean primary){return Map.of("contactType","GENERAL","fullName","Ána 山田","email","ana@example.invalid","phone","+81 (3) 1234-5678","isPrimary",primary);}
 @Test void duplicateCreationIsAtomicAndNormalizesCaseAndWhitespace()throws Exception{
  String code="EDGE"+next(),admin=superAdminToken();var body=creation(code);
  var first=post("/restaurants").as(admin).body(body).expect(201);UUID r=id(first);
  assertThat(first.get("ownerUserId").isNull()).isTrue();
  var branches=get("/restaurants/{r}/branches",r).as(admin).expect(200).get("items");assertThat(branches).hasSize(1);assertThat(branches.get(0).get("code").asText()).isEqualTo("MAIN");
  get("/restaurants/{r}/settings",r).as(admin).expect(200);
  var duplicate=new HashMap<>(body);duplicate.put("code"," "+code.toLowerCase()+" ");duplicate.put("slug",code.toLowerCase()+"-other");
  post("/restaurants").as(admin).body(duplicate).expect(409);
  assertThat(jdbcTemplate.queryForObject("select count(*) from "+SCHEMA+".restaurants where code=?",Integer.class,code)).isEqualTo(1);
 }
 @Test void simultaneousDuplicateCreationProducesExactlyOneRestaurantAndBranch()throws Exception{
  String code="RACE"+next(),admin=superAdminToken();var body=creation(code);
  try(var pool=Executors.newFixedThreadPool(2)){
   var start=new CountDownLatch(1);
   Callable<Integer> action=()->{start.await();return post("/restaurants").as(admin).body(body).send().status();};
   var a=pool.submit(action);var b=pool.submit(action);start.countDown();
   assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
  }
  assertThat(jdbcTemplate.queryForObject("select count(*) from "+SCHEMA+".branches b join "+SCHEMA+".restaurants r on r.id=b.restaurant_id where r.code=?",Integer.class,code)).isEqualTo(1);
 }
 @Test void validationRejectsWebsiteSchemesAndOversizedDataWithoutCreatingRecords()throws Exception{
  String code="INVALID"+next(),admin=superAdminToken();var valid=creation(code);
  for(var change:List.of(Map.of("website","javascript:alert(1)"),Map.of("website","https://user:secret@example.com"),Map.of("name","x".repeat(151)),Map.of("email","broken"),Map.of("phone","bad"),Map.of("currency","ZZZ"),Map.of("timezone","Mars/Phobos"))){
   var bad=new HashMap<>(valid);bad.putAll(change);post("/restaurants").as(admin).body(bad).expect(400);
  }
  assertThat(jdbcTemplate.queryForObject("select count(*) from "+SCHEMA+".restaurants where code=?",Integer.class,code)).isZero();
 }
 @Test void repeatedPrimarySelectionAndEditingKeepExactlyOnePrimaryAtRestaurantAndBranch()throws Exception{
  var w=newWorld("primary-repeat");String admin=superAdminToken();
  for(String base:List.of("/restaurants/"+w.restaurantId(),"/restaurants/"+w.restaurantId()+"/branches/"+w.branchId())){
   for(String type:List.of("addresses","contacts")){
    var body=type.equals("addresses")?address(true):contact(true);String route=base+"/"+type;
    var created=post(route).as(admin).body(body).expect(201);String id=created.get("id").asText();
    patch(route+"/"+id+"/primary").as(admin).expect(200);patch(route+"/"+id+"/primary").as(admin).expect(200);
    assertThat(get(route).as(admin).expect(200).get(0).get("isPrimary").asBoolean()).as(route+" repeated primary").isTrue();
    put(route+"/"+id).as(admin).body(body).expect(200);
    assertThat(get(route).as(admin).expect(200).get(0).get("isPrimary").asBoolean()).as(route+" edit primary").isTrue();
    delete(route+"/"+id).as(admin).expect(204);assertThat(get(route).as(admin).expect(200)).isEmpty();
   }
  }
 }
 @Test void concurrentPrimarySelectionAndTaxDefaultsPreserveUniqueness()throws Exception{
  var w=newWorld("primary-race");String admin=superAdminToken(),base="/restaurants/"+w.restaurantId();
  for(String type:List.of("addresses","contacts","tax-profiles")){
   Map<String,Object> body=type.equals("addresses")?address(false):type.equals("contacts")?contact(false):Map.of("country","AL","vatNumber","VAT","isDefault",false);
   String route=base+"/"+type;String a=post(route).as(admin).body(body).expect(201).get("id").asText();String b=post(route).as(admin).body(body).expect(201).get("id").asText();String suffix=type.equals("tax-profiles")?"default":"primary";
   try(var pool=Executors.newFixedThreadPool(2)){
    var start=new CountDownLatch(1);var f1=pool.submit(()->{start.await();return patch(route+"/"+a+"/"+suffix).as(admin).send().status();});var f2=pool.submit(()->{start.await();return patch(route+"/"+b+"/"+suffix).as(admin).send().status();});start.countDown();
    assertThat(List.of(f1.get(20,TimeUnit.SECONDS),f2.get(20,TimeUnit.SECONDS))).containsExactly(200,200);
   }
   var rows=get(route).as(admin).expect(200);int defaults=0;for(var row:rows)if(row.get(type.equals("tax-profiles")?"isDefault":"isPrimary").asBoolean())defaults++;assertThat(defaults).as(type).isEqualTo(1);
  }
 }
 @Test void repeatedTaxDefaultSelectionDoesNotRemoveTheDefaultAndBadDatesAreRejected()throws Exception{
  var w=newWorld("tax-repeat");String admin=superAdminToken(),route="/restaurants/"+w.restaurantId()+"/tax-profiles";
  var body=Map.of("country","AL","vatNumber","VAT","isDefault",true,"effectiveFrom","2030-01-01T00:00:00Z","effectiveTo","2031-01-01T00:00:00Z");String id=post(route).as(admin).body(body).expect(201).get("id").asText();
  patch(route+"/"+id+"/default").as(admin).expect(200);assertThat(get(route).as(admin).expect(200).get(0).get("isDefault").asBoolean()).isTrue();
  var bad=new HashMap<String,Object>(body);bad.put("effectiveTo","2030-01-01T00:00:00Z");put(route+"/"+id).as(admin).body(bad).expect(400);
  delete(route+"/"+id).as(admin).expect(204);assertThat(get(route).as(admin).expect(200)).isEmpty();
 }
 @Test void staleOrMissingEditVersionsCannotOverwriteRestaurantOrBranch()throws Exception{
  var w=newWorld("edit-version");String admin=superAdminToken();
  for(String route:List.of("/restaurants/"+w.restaurantId(),"/restaurants/"+w.restaurantId()+"/branches/"+w.branchId())){
   var original=get(route).as(admin).expect(200);var edit=objectMapper.convertValue(original,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
   edit.put("expectedVersion",original.get("version").asLong());edit.put("description","First writer");
   var saved=put(route).as(admin).body(edit).expect(200);assertThat(saved.get("version").asLong()).isGreaterThan(original.get("version").asLong());
   edit.put("description","Stale writer");put(route).as(admin).body(edit).expect(409);
   edit.remove("expectedVersion");put(route).as(admin).body(edit).expect(409);
   assertThat(get(route).as(admin).expect(200).get("description").asText()).isEqualTo("First writer");
   edit.put("expectedVersion",saved.get("version").asLong());put(route).as(admin).body(edit).expect(200);
  }
 }
 @Test void restaurantCardsValidatePaginationAndPersistReorder()throws Exception{
  var a=newWorld("picker-a");var b=newWorld("picker-b");String admin=superAdminToken();
  get("/restaurants/cards").as(admin).param("page",-1).expect(400);get("/restaurants/cards").as(admin).param("size",101).expect(400);get("/restaurants/cards").as(admin).param("filter","unknown").expect(400);
  put("/restaurants/order").as(admin).body(Map.of("restaurantIds",List.of(b.restaurantId(),a.restaurantId()))).expect(204);
  var page=get("/restaurants/cards").as(admin).param("size",1).expect(200);assertThat(page.get("items").get(0).get("id").asText()).isEqualTo(b.restaurantId().toString());
  var second=get("/restaurants/cards").as(admin).param("size",1).param("page",1).expect(200);assertThat(second.get("items").get(0).get("id").asText()).isEqualTo(a.restaurantId().toString());
  assertThat(get("/restaurants/cards").as(admin).param("search","nonexistent-"+UUID.randomUUID()).expect(200).get("items")).isEmpty();
 }
}
