package pos.pos.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public final class TestPostgresContainerSupport {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16");
    private static final int MAX_POOL_SIZE = 4;
    private static final int MIN_IDLE = 0;
    private static final long IDLE_TIMEOUT_MS = 10_000L;

    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName("pos")
            .withUsername("pos_user")
            .withPassword("pos_pass");

    static {
        POSTGRES.start();
    }

    private TestPostgresContainerSupport() {
    }

    public static void registerProdDatabaseProperties(DynamicPropertyRegistry registry, String schema) {
        registry.add("DB_URL", () -> jdbcUrlWithSchema(schema));
        registry.add("DB_USERNAME", POSTGRES::getUsername);
        registry.add("DB_PASSWORD", POSTGRES::getPassword);
        registerSharedPoolProperties(registry);
        registry.add("spring.flyway.default-schema", () -> schema);
        registry.add("spring.flyway.schemas[0]", () -> schema);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> schema);
    }

    public static void registerTestProfileDatabaseProperties(DynamicPropertyRegistry registry, String schema) {
        registry.add("spring.datasource.url", () -> jdbcUrlWithSchema(schema));
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registerSharedPoolProperties(registry);
        registry.add("spring.flyway.default-schema", () -> schema);
        registry.add("spring.flyway.schemas", () -> schema);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> schema);
    }

    private static void registerSharedPoolProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> MAX_POOL_SIZE);
        registry.add("spring.datasource.hikari.minimum-idle", () -> MIN_IDLE);
        registry.add("spring.datasource.hikari.idle-timeout", () -> IDLE_TIMEOUT_MS);
    }

    private static String jdbcUrlWithSchema(String schema) {
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        return POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema;
    }
}
