package pos.pos.restaurant.service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.enums.RestaurantStatus;
import java.util.*;
/** Closing or archiving must leave no unfinished operational or financial work behind. */
@Component
public class RestaurantClosureGuard {
 private final JdbcTemplate jdbc;private final String schema;
 public RestaurantClosureGuard(JdbcTemplate jdbc,@Value("${spring.jpa.properties.hibernate.default_schema:public}") String schema){
  if(!schema.matches("[A-Za-z_][A-Za-z0-9_]*"))throw new IllegalArgumentException("Invalid schema");this.jdbc=jdbc;this.schema="\""+schema+"\".";
 }
 public void restaurant(UUID id,RestaurantStatus status){
  if(status==RestaurantStatus.ACTIVE && usableBranches(id)==0)throw new AuthException("Open a branch before reopening the restaurant",HttpStatus.CONFLICT);
  // Suspension is the existing emergency access stop. Closing and archiving require settled work.
  if(status!=RestaurantStatus.INACTIVE && status!=RestaurantStatus.ARCHIVED)return;
  requireClear(work(id,null));
 }
 public void branch(Branch branch,boolean active){
  if(active)return;
  var reasons=new ArrayList<>(work(branch.getRestaurant().getId(),branch.getId()));
  if(branch.isActive() && branch.getRestaurant().isActive() && usableBranches(branch.getRestaurant().getId())<=1)
   reasons.add("the restaurant needs an active branch; close the restaurant first");
  requireClear(reasons);
 }
 public List<String> removalReasons(Branch branch){
  var reasons=new ArrayList<String>();UUID id=branch.getId();
  add(reasons,count("select count(*) from "+schema+"tables where branch_id=? and is_active=true",id),"active tables");
  add(reasons,count("select count(*) from "+schema+"inventory_locations l where l.branch_id=? and (l.is_active=true or exists(select 1 from "+schema+"inventory_levels v where v.location_id=l.id and (v.on_hand_quantity<>0 or v.committed_quantity<>0)))",id),"stock locations or remaining stock");
  add(reasons,count("select count(*) from "+schema+"kds_stations where branch_id=? and is_active=true",id),"active kitchen stations");
  add(reasons,count("select count(*) from "+schema+"waitlist_entries where branch_id=? and status='WAITING'",id),"waiting groups");
  if(branch.isActive()&&branch.getRestaurant().isActive()&&usableBranches(branch.getRestaurant().getId())<=1)reasons.add("the restaurant's last active branch");
  reasons.addAll(work(branch.getRestaurant().getId(),id));return reasons.stream().distinct().toList();
 }
 public List<String> archiveReasons(Branch branch) {
  UUID id=branch.getId();
  var reasons=new ArrayList<>(work(branch.getRestaurant().getId(),id));
  add(reasons,count("select count(*) from "+schema+"shifts where branch_id=? and status='SCHEDULED'",id),"scheduled shifts to cancel or move");
  add(reasons,count("select count(*) from "+schema+"inventory_locations l where l.branch_id=? and exists(select 1 from "+schema+"inventory_levels v where v.location_id=l.id and (v.on_hand_quantity<>0 or v.committed_quantity<>0))",id),"locations with remaining or committed stock");
  if(branch.isActive() && branch.getRestaurant().isActive() && usableBranches(branch.getRestaurant().getId())<=1)
   reasons.add("the restaurant's last active branch");
  return reasons;
 }
 private long usableBranches(UUID restaurant){return count("select count(*) from "+schema+"branches where restaurant_id=? and deleted_at is null and is_active=true and status='ACTIVE'",restaurant);}
 private List<String> work(UUID restaurant,UUID branch){
  var reasons=new ArrayList<String>();var args=branch==null?new Object[]{restaurant}:new Object[]{restaurant,branch};
  String scope="restaurant_id=?"+(branch==null?"":" and branch_id=?");
  add(reasons,count("select count(*) from "+schema+"orders where "+scope+" and status in ('DRAFT','OPEN')",args),"open orders");
  add(reasons,count("select count(*) from "+schema+"shifts where "+scope+" and status in ('OPEN','ON_BREAK')",args),"open shifts");
  add(reasons,count("select count(*) from "+schema+"reservations where "+scope+" and status in ('PENDING','CONFIRMED','CHECKED_IN','SEATED')",args),"unfinished bookings");
  add(reasons,count("select count(*) from "+schema+"reservation_payments p join "+schema+"reservations r on r.id=p.reservation_id where r.restaurant_id=?"+(branch==null?"":" and r.branch_id=?")+" and p.status='PENDING'",args),"pending booking payments");
  add(reasons,count("select count(*) from "+schema+"pre_orders p join "+schema+"reservations r on r.id=p.reservation_id where r.restaurant_id=?"+(branch==null?"":" and r.branch_id=?")+" and p.status='SCHEDULED'",args),"scheduled pre-orders");
  add(reasons,count("select count(*) from "+schema+"waitlist_entries where "+scope+" and status='WAITING'",args),"waiting groups");
  return reasons;
 }
 private long count(String sql,Object...args){Long n=jdbc.queryForObject(sql,Long.class,args);return n==null?0:n;}
 private static void add(List<String> reasons,long count,String label){if(count>0)reasons.add(count+" "+label);}
 private static void requireClear(List<String> reasons){if(!reasons.isEmpty())throw new AuthException("Finish or move these before closing: "+String.join(", ",reasons),HttpStatus.CONFLICT);}
}
