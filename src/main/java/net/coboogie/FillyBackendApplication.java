package net.coboogie;

import com.google.cloud.spring.autoconfigure.pubsub.GcpPubSubAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(
    scanBasePackages = "net.coboogie",
    exclude = {GcpPubSubAutoConfiguration.class}
)
@EnableJpaRepositories(basePackages = "net.coboogie")
public class FillyBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(FillyBackendApplication.class, args);
	}

}
