package pos.pos.integration;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import pos.pos.integration.support.AbstractPosApiIntegrationTest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
class RestaurantInvitationSafetyIntegrationTest extends AbstractPosApiIntegrationTest {
 @org.springframework.beans.factory.annotation.Autowired private pos.pos.mail.EmailOutboxDispatcher dispatcher;
 private Map<String,Object> creation(String code){return Map.of("name","Invite "+code,"legalName","Invite LLC","code",code,"slug",code.toLowerCase(),"currency","EUR","timezone","Europe/Berlin","email","restaurant@example.invalid","phone","+355691112222");}
 private Map<String,Object> owner(String email){return Map.of("firstName","Owner","lastName","Test","email",email,"username","invite"+next());}
 private AtomicReference<String> capture(){
  var token=new AtomicReference<String>();doAnswer(call->{String text=call.getArgument(0,SimpleMailMessage.class).getText();var m=java.util.regex.Pattern.compile("[?&]token=([^\\s]+)").matcher(text);if(m.find())token.set(java.net.URLDecoder.decode(m.group(1),java.nio.charset.StandardCharsets.UTF_8));return null;}).when(mailSender).send(any(SimpleMailMessage.class));return token;
 }
 @Test void smtpOutageQueuesTheInvitationAndSendsItOnceTheMailServerIsBack()throws Exception{
  // Invitations go through the email outbox: a mail server outage no longer fails (or rolls back) the restaurant.
  String code="MAILFAIL"+next(),email="fail"+next()+"@example.invalid";var body=new HashMap<>(creation(code));body.put("owner",owner(email));
  doThrow(new org.springframework.mail.MailSendException("Injected SMTP outage")).when(mailSender).send(any(SimpleMailMessage.class));
  var saved=post("/restaurants").as(superAdminToken()).body(body).expect(201);
  assertThat(jdbcTemplate.queryForObject("select count(*) from "+SCHEMA+".restaurants where code=?",Integer.class,code)).isEqualTo(1);
  var owner=userRepository.findByEmailAndDeletedAtIsNull(email).orElseThrow();
  assertThat(get("/restaurants/{r}/branches",id(saved)).as(superAdminToken()).expect(200).get("items")).hasSize(1);
  var outbox=jdbcTemplate.queryForMap("select id,status,attempts,last_error from "+SCHEMA+".email_outbox where user_id=? and kind='OWNER_INVITE'",owner.getId());
  assertThat(outbox.get("status")).isEqualTo("PENDING");assertThat(((Number)outbox.get("attempts")).intValue()).isEqualTo(1);assertThat((String)outbox.get("last_error")).isNotBlank();
  var captured=capture();
  jdbcTemplate.update("update "+SCHEMA+".email_outbox set next_attempt_at=now() where id=?",outbox.get("id"));
  dispatcher.sendNow((UUID)outbox.get("id"));
  assertThat(jdbcTemplate.queryForObject("select status from "+SCHEMA+".email_outbox where id=?",String.class,outbox.get("id"))).isEqualTo("SENT");
  assertThat(captured.get()).isNotBlank();
  post("/auth/reset-password").body(Map.of("token",captured.get(),"newPassword","OwnerTest123!new")).expect(204);
 }
 @Test void supersededExpiredAndReusedOwnerInvitationsAreRejected()throws Exception{
  var captured=capture();String admin=superAdminToken(),code="INVITE"+next(),email="owner"+next()+"@example.invalid";
  var r=post("/restaurants").as(admin).body(creation(code)).expect(201);String root="/restaurants/"+r.get("id").asText();
  var added=post(root+"/owner").as(admin).body(owner(email)).expect(201);String ownerId=added.get("ownerUserId").asText(),first=captured.get();assertThat(first).isNotBlank();
  post(root+"/owner/invite").as(admin).expect(204);String second=captured.get();assertThat(second).isNotEqualTo(first);
  assertThat(post("/auth/reset-password").body(Map.of("token",first,"newPassword","OwnerTest123!new")).expect(400).get("message").asText()).contains("newer invitation");
  jdbcTemplate.update("update "+SCHEMA+".auth_password_reset_tokens set created_at=now()-interval '2 days',expires_at=now()-interval '1 second' where user_id=?",UUID.fromString(ownerId));
  assertThat(post("/auth/reset-password").body(Map.of("token",second,"newPassword","OwnerTest123!new")).expect(400).get("message").asText()).isNotBlank();
  post(root+"/owner/invite").as(admin).expect(204);String third=captured.get();
  post("/auth/reset-password").body(Map.of("token",third,"newPassword","OwnerTest123!new")).expect(204);
  post("/auth/reset-password").body(Map.of("token",third,"newPassword","OwnerTest123!new")).expect(400);
  post(root+"/owner/invite").as(admin).expect(409);
  assertThat(userRepository.findById(UUID.fromString(ownerId)).orElseThrow().isEmailVerified()).isTrue();
 }
 @Test void simultaneousInvitationUseHasExactlyOneWinner()throws Exception{
  var captured=capture();String admin=superAdminToken(),code="INVITERACE"+next();var body=new HashMap<>(creation(code));body.put("owner",owner("race"+next()+"@example.invalid"));post("/restaurants").as(admin).body(body).expect(201);String token=captured.get();
  try(var pool=Executors.newFixedThreadPool(2)){
   var start=new CountDownLatch(1);Callable<Integer> reset=()->{start.await();return post("/auth/reset-password").body(Map.of("token",token,"newPassword","OwnerTest123!new")).send().status();};
   var a=pool.submit(reset);var b=pool.submit(reset);start.countDown();assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(204,400);
  }
 }
}
