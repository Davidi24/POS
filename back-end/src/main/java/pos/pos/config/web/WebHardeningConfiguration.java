package pos.pos.config.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
public class WebHardeningConfiguration {

    @Bean
    FilterRegistrationBean<RequestSizeLimitFilter> requestSizeLimitFilter(
            @Value("${app.web.max-request-body-bytes:" + RequestSizeLimitFilter.DEFAULT_LIMIT + "}") long limit
    ) {
        var registration = new FilterRegistrationBean<>(new RequestSizeLimitFilter(limit));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        return registration;
    }
}
