package pos.pos.integration;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import pos.pos.integration.support.AbstractPosApiIntegrationTest;
import pos.pos.menu.image.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
/** Real R2 plus actual controllers, authorization and PostgreSQL transactions. Test bucket only. */
@EnabledIfEnvironmentVariable(named="RUN_R2_TESTS",matches="true")
@Import(RestaurantR2LiveIntegrationTest.LiveConfiguration.class)
class RestaurantR2LiveIntegrationTest extends AbstractPosApiIntegrationTest {
 @TestConfiguration static class LiveConfiguration {
  @Bean @Primary LiveStorage liveStorage(){return new LiveStorage();}
 }
 static class LiveStorage extends R2MenuImageStorage {
  final Set<String> keys=java.util.concurrent.ConcurrentHashMap.newKeySet();volatile boolean failWrites,failReads,failDeletes;
  final R2MenuImageStorage denied;
  LiveStorage(){super(System.getenv("R2_ENDPOINT"),System.getenv("R2_ACCESS_KEY_ID"),System.getenv("R2_SECRET_ACCESS_KEY"),System.getenv("R2_BUCKET"),System.getenv("R2_PUBLIC_BASE_URL"));
   if(!"pos-testing".equals(System.getenv("R2_BUCKET")))throw new IllegalArgumentException("Dedicated test bucket required");
   denied=new R2MenuImageStorage(System.getenv("R2_ENDPOINT"),System.getenv("R2_ACCESS_KEY_ID"),System.getenv("R2_SECRET_ACCESS_KEY"),"pos-testing-not-authorized","");
  }
  @Override public String store(UUID r,byte[] bytes,String type,String ext){if(failWrites)return denied.store(r,bytes,type,ext);String key=super.store(r,bytes,type,ext);keys.add(key);return key;}
  @Override public Optional<StoredPhoto> load(String key){return failReads?denied.load(key):super.load(key);}
  @Override public void delete(String key){if(failDeletes){denied.delete(key);return;}super.delete(key);keys.remove(key);}
  @Override public void destroy(){denied.destroy();super.destroy();}
 }
 @Override protected boolean isExpectedServerError(org.springframework.test.web.servlet.MvcResult result) throws Exception {
  return storage.failReads && result.getResponse().getStatus()==503
    && result.getRequest().getMethod().equals("GET")
    && result.getRequest().getRequestURI().startsWith("/public/menu-images/")
    && result.getResponse().getContentAsString().contains("Photos can't be loaded right now");
 }
 @Autowired LiveStorage storage;
 @Autowired StoredImageCleanup cleanup;
 @AfterEach void removeRemoteTestObjects(){storage.failWrites=false;storage.failReads=false;storage.failDeletes=false;for(String key:List.copyOf(storage.keys))storage.delete(key);}
 private byte[] png(int colour)throws Exception{
  var image=new BufferedImage(256,256,BufferedImage.TYPE_INT_RGB);for(int y=0;y<256;y++)for(int x=0;x<256;x++)image.setRGB(x,y,colour^(x*997+y*733));
  var out=new ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",out);return out.toByteArray();
 }
 private JsonNode upload(String path,String token,byte[] bytes,String type,int status)throws Exception{
  var result=mockMvc.perform(multipart(HttpMethod.PUT,path).file(new MockMultipartFile("file","test.png",type,bytes)).header("Authorization","Bearer "+token)).andReturn();
  assertThat(result.getResponse().getStatus()).as(path).isEqualTo(status);return objectMapper.readTree(result.getResponse().getContentAsByteArray());
 }
 private String key(String url){return storage.keyOf(url).orElseThrow();}
 @Test void logoCoversAndMenuImagesPersistDisplayReplaceAndDeleteThroughTheApi()throws Exception{
  var w=newWorld("r2-api");String admin=superAdminToken(),root="/restaurants/"+w.restaurantId();byte[] first=png(0x00aa00),second=png(0xaa0000);
  String logo=upload(root+"/branding/logo",w.ownerToken(),first,"image/png",200).get("logoUrl").asText();
  assertThat(get(root+"/branding").as(admin).expect(200).get("logoUrl").asText()).isEqualTo(logo);
  var served=mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(MenuImageStorage.backendUrl(key(logo)))).andReturn().getResponse();
  assertThat(served.getStatus()).isEqualTo(200);assertThat(served.getContentAsByteArray()).isEqualTo(first);assertThat(served.getContentType()).startsWith("image/png");
  if(logo.startsWith("https://")){
   var publicImage=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(logo)).timeout(java.time.Duration.ofSeconds(20)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
   assertThat(publicImage.statusCode()).as("Public R2 image URL displays").isEqualTo(200);assertThat(publicImage.body()).isEqualTo(first);
  }
  try(var restart=new RestartedStorage()) {assertThat(restart.storage.load(key(logo)).orElseThrow().content()).isEqualTo(first);}
  String replaced=upload(root+"/branding/logo",w.ownerToken(),second,"image/png",200).get("logoUrl").asText();
  assertThat(replaced).isNotEqualTo(logo);assertThat(storage.load(key(logo))).isEmpty();
  for(String route:List.of(root+"/cover",root+"/branches/"+w.branchId()+"/cover")){
   String url=upload(route,admin,first,"image/png",200).get("coverUrl").asText();assertThat(storage.load(key(url))).isPresent();
   delete(route).as(admin).expect(200);assertThat(storage.load(key(url))).isEmpty();
  }
  var menu=menu(w,"R2 menu");var item=item(w,menu,"R2 dish","12");String imagePath="/menus/"+menu.get("menuId")+"/sections/"+menu.get("sectionId")+"/items/"+item+"/image";
  String dish=upload(imagePath,w.ownerToken(),first,"image/png",200).get("imageUrl").asText();assertThat(storage.load(key(dish))).isPresent();
  delete(imagePath).as(w.ownerToken()).expect(200);assertThat(storage.load(key(dish))).isEmpty();
  put(root+"/branding").as(w.ownerToken()).body(Map.of("logoUrl","","primaryColor","#112233")).expect(200);assertThat(storage.load(key(replaced))).isEmpty();
  String last=upload(root+"/branding/logo",w.ownerToken(),first,"image/png",200).get("logoUrl").asText();
  delete(root+"/branding/logo").as(w.ownerToken()).expect(200);assertThat(storage.load(key(last))).isEmpty();
 }
 @Test void actualR2FailuresPreserveCurrentImageAndCleanupRecovers()throws Exception{
  var w=newWorld("r2-failure");String root="/restaurants/"+w.restaurantId();byte[] bytes=png(123);
  String old=upload(root+"/branding/logo",w.ownerToken(),bytes,"image/png",200).get("logoUrl").asText();
  storage.failWrites=true;upload(root+"/branding/logo",w.ownerToken(),bytes,"image/png",503);storage.failWrites=false;
  assertThat(get(root+"/branding").as(w.ownerToken()).expect(200).get("logoUrl").asText()).isEqualTo(old);
  storage.failReads=true;get(MenuImageStorage.backendUrl(key(old))).expect(503);storage.failReads=false;
  assertThat(storage.load(key(old))).isPresent();
  storage.failDeletes=true;
  String current=upload(root+"/branding/logo",w.ownerToken(),png(456),"image/png",200).get("logoUrl").asText();
  assertThat(storage.load(key(old))).isPresent();assertThat(storage.load(key(current))).isPresent();
  assertThat(jdbcTemplate.queryForObject("select attempts from "+SCHEMA+".stored_image_cleanup where object_key=?",Integer.class,key(old))).isGreaterThan(0);
  storage.failDeletes=false;cleanup.processKey(key(old));assertThat(storage.load(key(old))).isEmpty();
  assertThat(get(root+"/branding").as(w.ownerToken()).expect(200).get("logoUrl").asText()).isEqualTo(current);
 }
 @Test void invalidImagesAndWrongPermissionsNeverWriteToR2()throws Exception{
  var w=newWorld("r2-validation");String root="/restaurants/"+w.restaurantId();int before=storage.keys.size();
  upload(root+"/branding/logo",w.ownerToken(),new byte[]{(byte)137,80,78,71,13,10,26,10},"image/png",415);
  upload(root+"/branding/logo",w.ownerToken(),"not an image".getBytes(),"image/png",415);
  upload(root+"/branding/logo",w.ownerToken(),png(123),"image/gif",415);
  upload(root+"/branding/logo",w.ownerToken(),new byte[5*1024*1024+1],"image/png",413);
  upload(root+"/cover",w.ownerToken(),png(123),"image/png",403);
  var other=newWorld("r2-other");upload(root+"/branding/logo",other.ownerToken(),png(123),"image/png",403);
  assertThat(storage.keys.size()).isEqualTo(before);
 }
 private record RestartedStorage(R2MenuImageStorage storage) implements AutoCloseable {
  RestartedStorage(){this(new R2MenuImageStorage(System.getenv("R2_ENDPOINT"),System.getenv("R2_ACCESS_KEY_ID"),System.getenv("R2_SECRET_ACCESS_KEY"),System.getenv("R2_BUCKET"),System.getenv("R2_PUBLIC_BASE_URL")));}
  public void close(){storage.destroy();}
 }
}
