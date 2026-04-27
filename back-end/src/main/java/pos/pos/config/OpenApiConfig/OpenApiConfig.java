package pos.pos.config.OpenApiConfig;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes(
                                "bearerAuth",
                                new SecurityScheme()
                                        .name("bearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        )
                );
    }

    @Bean
    public GroupedOpenApi authenticationGroup() {
        return GroupedOpenApi.builder()
                .group("Authentication")
                .pathsToMatch("/auth", "/auth/**")
                .pathsToExclude("/auth/device", "/auth/device/**")
                .build();
    }

    @Bean
    public GroupedOpenApi userGroup() {
        return GroupedOpenApi.builder()
                .group("Users")
                .pathsToMatch("/users", "/users/**")
                .build();
    }

    @Bean
    public GroupedOpenApi roleGroup() {
        return GroupedOpenApi.builder()
                .group("Roles")
                .pathsToMatch("/roles", "/roles/**", "/permissions", "/permissions/**")
                .build();
    }

    @Bean
    public GroupedOpenApi restaurantGroup() {
        return GroupedOpenApi.builder()
                .group("Restaurants")
                .pathsToMatch("/restaurants", "/restaurants/**")
                .build();
    }

    @Bean
    public GroupedOpenApi settingsGroup() {
        return GroupedOpenApi.builder()
                .group("Settings")
                .pathsToMatch("/settings", "/settings/**")
                .build();
    }

    @Bean
    public GroupedOpenApi deviceGroup() {
        return GroupedOpenApi.builder()
                .group("Devices")
                .pathsToMatch("/devices", "/devices/**", "/auth/device", "/auth/device/**")
                .build();
    }
}
