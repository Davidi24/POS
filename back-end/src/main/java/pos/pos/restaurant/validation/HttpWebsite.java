package pos.pos.restaurant.validation;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;
@Target({ElementType.FIELD,ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy=HttpWebsiteValidator.class)
public @interface HttpWebsite {
 String message() default "website must be an http or https URL without login credentials";
 Class<?>[] groups() default {};
 Class<? extends Payload>[] payload() default {};
}
