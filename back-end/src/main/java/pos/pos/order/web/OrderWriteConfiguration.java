package pos.pos.order.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import pos.pos.restaurant.service.RestaurantScopeService;

@Configuration(proxyBeanMethods = false)
public class OrderWriteConfiguration {
    @Bean
    FilterRegistrationBean<OrderWriteFilter> orderWriteFilter(JdbcTemplate jdbc, PlatformTransactionManager manager,
            RestaurantScopeService scope, @Value("${spring.jpa.properties.hibernate.default_schema:public}") String schema) {
        var filter = new OrderWriteFilter(jdbc, manager, scope);
        filter.setSchema(schema);
        var registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
        return registration;
    }
}
