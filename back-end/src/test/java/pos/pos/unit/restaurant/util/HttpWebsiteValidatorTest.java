package pos.pos.unit.restaurant.util;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import pos.pos.restaurant.validation.HttpWebsiteValidator;
import static org.assertj.core.api.Assertions.*;
class HttpWebsiteValidatorTest {
 private final HttpWebsiteValidator validator=new HttpWebsiteValidator();
 @ParameterizedTest @ValueSource(strings={"https://example.com"," HTTP://example.com/path?q=a#menu ","https://bücher.de/menu","https://example.com:8443","http://[::1]:8080","","   "})
 void acceptsOptionalAndWebAddresses(String url){assertThat(validator.isValid(url,null)).isTrue();}
 @ParameterizedTest @ValueSource(strings={"javascript:alert(1)","data:image/png,x","ftp://example.com","https://","https://example .com","https://user:pass@example.com","https://example.com:70000","https://example.com:","//example.com","example.com","https://example.com/a\nb"})
 void rejectsUnsafeOrMalformedAddresses(String url){assertThat(validator.isValid(url,null)).isFalse();}
 @Test void nullClearsOptionalWebsite(){assertThat(validator.isValid(null,null)).isTrue();}
}
