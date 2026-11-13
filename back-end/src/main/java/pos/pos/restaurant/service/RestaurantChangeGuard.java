package pos.pos.restaurant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pos.pos.exception.auth.AuthException;
import pos.pos.restaurant.entity.Restaurant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/** Binds dangerous changes to the reviewed data, and makes a successful retry return the original result. */
@Component
public class RestaurantChangeGuard {
 private final JdbcTemplate jdbc; private final ObjectMapper json; private final EntityManager entities; private final String schema;
 public RestaurantChangeGuard(JdbcTemplate jdbc,ObjectMapper json,EntityManager entities,
       @Value("${spring.jpa.properties.hibernate.default_schema:public}") String schema) {
  if(!schema.matches("[A-Za-z_][A-Za-z0-9_]*"))throw new IllegalArgumentException("Invalid schema");
  this.jdbc=jdbc;this.json=json;this.entities=entities;this.schema="\""+schema+"\".";
 }
 public void lock(Restaurant restaurant) {
  jdbc.queryForList("select id from "+schema+"restaurants where id=? for update",restaurant.getId());
  entities.refresh(restaurant);
 }
 public String hash(Object... values) {
  try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(values)));}
  catch(Exception e){throw new IllegalStateException("Could not fingerprint restaurant change",e);}
 }
 public void requireCurrent(String supplied,String current) {
  if(supplied==null || !supplied.equals(current))throw new AuthException("The restaurant or its data changed. Refresh the preview and confirm again.",HttpStatus.CONFLICT);
 }
 public <T> T replay(UUID restaurant,String operation,String key,Object request,Class<T> type) {
  if(key==null)return null;
  var rows=jdbc.queryForList("select request_hash,response_json from "+schema+"restaurant_change_replays where restaurant_id=? and operation=? and request_key=?",restaurant,operation,key);
  if(rows.isEmpty())return null;
  if(!hash(request).equals(rows.getFirst().get("request_hash")))throw new AuthException("This preview was already applied with different choices. Refresh it before trying again.",HttpStatus.CONFLICT);
  try{return json.readValue((String)rows.getFirst().get("response_json"),type);}
  catch(Exception e){throw new IllegalStateException("Could not read completed change",e);}
 }
 public void completed(UUID restaurant,String operation,String key,Object request,Object response) {
  try{jdbc.update("insert into "+schema+"restaurant_change_replays(restaurant_id,operation,request_key,request_hash,response_json) values (?,?,?,?,?)",restaurant,operation,key,hash(request),json.writeValueAsString(response));}
  catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
 }
}
