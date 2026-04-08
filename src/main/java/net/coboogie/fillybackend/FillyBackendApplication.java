package net.coboogie.fillybackend;

import com.google.cloud.spring.autoconfigure.pubsub.GcpPubSubAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {GcpPubSubAutoConfiguration.class})
public class FillyBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(FillyBackendApplication.class, args);
	}

}
