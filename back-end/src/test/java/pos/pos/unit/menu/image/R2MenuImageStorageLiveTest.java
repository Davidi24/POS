package pos.pos.unit.menu.image;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import pos.pos.menu.image.R2MenuImageStorage;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Opt-in only: never run with real credentials during an ordinary test suite. */
@EnabledIfEnvironmentVariable(named="RUN_R2_TESTS",matches="true")
class R2MenuImageStorageLiveTest {
 private R2MenuImageStorage open(String bucket) {
  return new R2MenuImageStorage(System.getenv("R2_ENDPOINT"),System.getenv("R2_ACCESS_KEY_ID"),System.getenv("R2_SECRET_ACCESS_KEY"),bucket,"");
 }
 @Test void realCloudflareUploadSurvivesClientRestartReplacementDeletionAndProviderFailure() throws Exception {
  String bucket=System.getenv("R2_BUCKET");
  assertThat(bucket).as("Dedicated test bucket required").isEqualTo("pos-testing");
  var pixels=new BufferedImage(512,512,BufferedImage.TYPE_INT_RGB);
  var random=new Random(17);
  for(int y=0;y<512;y++)for(int x=0;x<512;x++)pixels.setRGB(x,y,random.nextInt());
  var bytes=new ByteArrayOutputStream();ImageIO.write(pixels,"png",bytes);byte[] png=bytes.toByteArray();
  var keys=new ArrayList<String>();UUID restaurant=UUID.randomUUID();
  R2MenuImageStorage storage=open(bucket);
  try {
   String first=storage.store(restaurant,png,"image/png",".png");keys.add(first);
   assertThat(storage.load(first).orElseThrow().content()).isEqualTo(png);
   storage.destroy();storage=open(bucket);
   assertThat(storage.load(first).orElseThrow().content()).as("Persistent after new storage client").isEqualTo(png);
   pixels.setRGB(0,0,0);bytes.reset();ImageIO.write(pixels,"png",bytes);byte[] replacement=bytes.toByteArray();
   String second=storage.store(restaurant,replacement,"image/png",".png");keys.add(second);
   assertThat(second).isNotEqualTo(first);
   assertThat(storage.load(second).orElseThrow().content()).isEqualTo(replacement);
   storage.delete(first);assertThat(storage.load(first)).isEmpty();keys.remove(first);
   try(var unavailable=new CloseableStorage(open(bucket+"-not-authorized"))) {
    assertThatThrownBy(()->unavailable.storage.store(restaurant,png,"image/png",".png")).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(503));
    assertThatThrownBy(()->unavailable.storage.load(second)).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(503));
    assertThatThrownBy(()->unavailable.storage.delete(second)).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(503));
   }
   assertThat(storage.load(second).orElseThrow().content()).as("Failed operation preserved current object").isEqualTo(replacement);
   System.out.println("R2 live lifecycle passed: 2 image uploads, 5 successful/missing reads, 3 deliberate denied operations; under 2 MB uploaded; test objects cleaned in finally.");
  }finally{for(String key:keys)storage.delete(key);storage.destroy();}
 }
 private record CloseableStorage(R2MenuImageStorage storage) implements AutoCloseable {public void close(){storage.destroy();}}
}
