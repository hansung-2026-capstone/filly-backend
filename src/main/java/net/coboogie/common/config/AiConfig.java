package net.coboogie.common.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI ChatClient 설정.
 * <p>
 * Spring AI는 {@code ChatClient.Builder}를 자동으로 빈으로 등록하므로,
 * 이 클래스에서 Builder를 주입받아 실제 사용할 {@code ChatClient} 빈을 생성한다.
 * API 키 등 설정값은 application.properties의 {@code spring.ai.openai.*}에서 읽힌다.
 */
@Configuration
public class AiConfig {

    /**
     * Spring AI가 자동 구성한 Builder로 ChatClient 빈을 생성한다.
     * 서비스 레이어에서 이 빈을 주입받아 OpenAI API를 호출한다.
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
