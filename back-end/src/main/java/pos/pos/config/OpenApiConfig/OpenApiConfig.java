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
                .packagesToScan("pos.pos.restaurant.controller")
                .build();
    }

    @Bean
    public GroupedOpenApi settingsGroup() {
        return GroupedOpenApi.builder()
                .group("Settings")
                .packagesToScan("pos.pos.settings.controller")
                .build();
    }

    @Bean
    public GroupedOpenApi tablesGroup() {
        return GroupedOpenApi.builder()
                .group("Tables")
                .packagesToScan("pos.pos.tables.controller")
                .build();
    }

    @Bean
    public GroupedOpenApi reservationsGroup() {
        return GroupedOpenApi.builder()
                .group("Reservations")
                .packagesToScan("pos.pos.reservation.controller")
                .build();
    }

    @Bean
    public GroupedOpenApi deviceGroup() {
        return GroupedOpenApi.builder()
                .group("Devices")
                .pathsToMatch(
                        "/restaurants/*/settings/printers",
                        "/restaurants/*/settings/printers/**",
                        "/restaurants/*/branches/*/printer-routes",
                        "/restaurants/*/branches/*/printer-routes/**",
                        "/restaurants/*/branches/*/devices",
                        "/restaurants/*/branches/*/devices/**",
                        "/restaurants/*/devices/*/assignments",
                        "/restaurants/*/devices/*/assignments/**",
                        "/restaurants/*/devices/*/pairing-tokens",
                        "/restaurants/*/devices/*/pairing-tokens/**"
                )
                .build();
    }

    @Bean
    public GroupedOpenApi menuGroup() {
        return GroupedOpenApi.builder()
                .group("Menus")
                .pathsToMatch(
                        "/menus",
                        "/menus/**",
                        "/option-group-types",
                        "/option-group-types/**",
                        "/option-groups",
                        "/option-groups/**",
                        "/public/restaurants/*/menus",
                        "/public/restaurants/*/menus/**"
                )
                .build();
    }

    @Bean
    public GroupedOpenApi inventoryGroup() {
        return GroupedOpenApi.builder()
                .group("Inventory")
                .packagesToScan("pos.pos.inventory.controller")
                .build();
    }

    @Bean
    public GroupedOpenApi recipeGroup() {
        return GroupedOpenApi.builder()
                .group("Recipes")
                .packagesToScan("pos.pos.recipe.controller")
                .build();
    }
}
