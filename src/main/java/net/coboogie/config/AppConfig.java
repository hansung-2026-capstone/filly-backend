package net.coboogie.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestTemplate;

/**
 * 애플리케이션 공통 설정.
 * <p>
 * {@code @EnableAsync}로 {@code @Async} 메서드의 비동기 실행을 활성화한다.
 */
@Configuration
@EnableAsync
public class AppConfig {

    /**
     * 공통 HTTP 클라이언트 빈.
     *
     * @return RestTemplate 인스턴스
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
