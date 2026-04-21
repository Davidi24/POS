package pos.pos.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class AbstractTestProfilePostgresTest {

    protected static final String TEST_SCHEMA = "app_test";

    @DynamicPropertySource
    static void registerTestDatabaseProperties(DynamicPropertyRegistry registry) {
        TestPostgresContainerSupport.registerTestProfileDatabaseProperties(registry, TEST_SCHEMA);
    }
}
