package pos.pos;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pos.pos.support.AbstractTestProfilePostgresTest;

@SpringBootTest
@ActiveProfiles("test")
class PosApplicationTests extends AbstractTestProfilePostgresTest {

	@Test
	void contextLoads() {
	}
}
