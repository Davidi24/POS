package pos.pos.restaurant.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;
import java.time.*;
import java.util.*;

/** Never silently chooses an offset in a DST gap or overlap. */
@Component
public class TimezoneBookingGuard {
 private final JdbcTemplate jdbc; private final String schema;
 public TimezoneBookingGuard(JdbcTemplate jdbc,@Value("${spring.jpa.properties.hibernate.default_schema:public}") String schema){
  if(!schema.matches("[A-Za-z_][A-Za-z0-9_]*"))throw new IllegalArgumentException("Invalid schema");
  this.jdbc=jdbc;this.schema="\""+schema+"\".";
 }
 public static OffsetDateTime keepClock(OffsetDateTime time,ZoneId oldZone,ZoneId newZone){
  LocalDateTime local=time.atZoneSameInstant(oldZone).toLocalDateTime();
  var offsets=newZone.getRules().getValidOffsets(local);
  if(offsets.size()!=1)throw new AuthException("The booking time "+local+" is missing or repeated in "+newZone+" because of daylight saving. Keep the exact moment, or resolve this booking before changing the time zone.",HttpStatus.CONFLICT);
  return local.atOffset(offsets.getFirst());
 }
 public void openingHours(UUID branch,OffsetDateTime start,OffsetDateTime end,ZoneId zone){
  LocalDateTime from=start.atZoneSameInstant(zone).toLocalDateTime(),to=end.atZoneSameInstant(zone).toLocalDateTime();
  boolean configured=Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from "+schema+"\"settings-business-hours\" where branch_id=?)",Boolean.class,branch));
  // With no weekly schedule configured, only explicit special-day restrictions apply.
  var specialToday=hours(branch,from.toLocalDate(),true);
  if(!specialToday.isEmpty() && Boolean.TRUE.equals(specialToday.getFirst().get("is_closed")))throw conflict();
  if(!configured && specialToday.isEmpty())return;
  for(LocalDate date:List.of(from.toLocalDate().minusDays(1),from.toLocalDate())){
   var special=hours(branch,date,true);
   var rows=special.isEmpty()?hours(branch,date,false):special;
   if(rows.isEmpty()||Boolean.TRUE.equals(rows.getFirst().get("is_closed")))continue;
   var row=rows.getFirst();LocalTime open=((java.sql.Time)row.get("open_time")).toLocalTime(),close=((java.sql.Time)row.get("close_time")).toLocalTime();
   LocalDateTime a=date.atTime(open),b=date.atTime(close);
   if(special.isEmpty() && Boolean.TRUE.equals(row.get("is_overnight")))b=b.plusDays(1);
   if(!from.isBefore(a)&&!to.isAfter(b))return;
  }
  throw conflict();
 }
 private List<Map<String,Object>> hours(UUID branch,LocalDate date,boolean special){
  return jdbc.queryForList("select * from "+schema+(special?"\"settings-special-hours\" where branch_id=? and special_date=?":"\"settings-business-hours\" where branch_id=? and day_of_week=?"),branch,special?date:date.getDayOfWeek().getValue());
 }
 private AuthException conflict(){return new AuthException("A booking would fall outside the branch's opening hours. Resolve it before changing the time zone.",HttpStatus.CONFLICT);}
}
