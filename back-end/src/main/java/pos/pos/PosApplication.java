package pos.pos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import pos.pos.config.properties.BootstrapSuperAdminProperties;

@SpringBootApplication
@EnableConfigurationProperties(BootstrapSuperAdminProperties.class)
public class PosApplication {

	public static void main(String[] args) {
		SpringApplication.run(PosApplication.class, args);
		System.out.println("System started successfully");
	}

}

