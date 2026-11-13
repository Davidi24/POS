package pos.pos.payment.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
/** Immutable receipt identity captured by database triggers in the payment/close transaction. */
@Service
public class ReceiptIdentities {
 public record Identity(String restaurantName,String legalName,String branchName,List<String> addressLines,
   String phone,String taxNumber,String vatNumber,boolean showLogo,String logoUrl,String footerNote){}
 private final JdbcTemplate jdbc;private final ObjectMapper json;private final String schema;
 public ReceiptIdentities(JdbcTemplate jdbc,ObjectMapper json,@Value("${spring.jpa.properties.hibernate.default_schema:public}") String schema){
  if(!schema.matches("[A-Za-z_][A-Za-z0-9_]*"))throw new IllegalArgumentException("Invalid schema");this.jdbc=jdbc;this.json=json;this.schema="\""+schema+"\".";
 }
 public Optional<Identity> find(UUID order){
  return jdbc.query("select identity_json::text from "+schema+"order_receipt_identities where order_id=?",(row,n)->{
   try{return json.readValue(row.getString(1),Identity.class);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Invalid saved receipt identity",e);}
  },order).stream().findFirst();
 }
}
