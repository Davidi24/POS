package pos.pos.restaurant.validation;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;
import java.net.IDN;
public final class HttpWebsiteValidator implements ConstraintValidator<HttpWebsite,String> {
 @Override public boolean isValid(String value,ConstraintValidatorContext context) {
  if(value==null || value.isBlank())return true;
  String text=value.trim();
  try {
   URI uri=new URI(text);
   if(!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme()))return false;
   if(uri.getRawAuthority()==null || uri.getRawAuthority().contains("@") || uri.getRawAuthority().endsWith(":"))return false;
   String host=uri.toURL().getHost();
   if(host==null || host.isBlank() || text.chars().anyMatch(Character::isWhitespace))return false;
   if(!host.startsWith("["))IDN.toASCII(host,IDN.USE_STD3_ASCII_RULES);
   return uri.getPort()==-1 || uri.getPort()>0 && uri.getPort()<=65535;
  } catch(Exception invalid){return false;}
 }
}
