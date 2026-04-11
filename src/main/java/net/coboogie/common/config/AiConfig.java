package net.coboogie.common.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Spring AI ChatClient 설정.
 * <p>
 * Spring AI는 {@code ChatClient.Builder}를 자동으로 빈으로 등록하므로,
 * 이 클래스에서 Builder를 주입받아 실제 사용할 {@code ChatClient} 빈을 생성한다.
 * API 키 등 설정값은 application.properties의 {@code spring.ai.vertex.ai.gemini.*}에서 읽힌다.
 * 시스템 프롬프트는 {@code classpath:prompts/diary-system.txt}에서 관리한다.
 */
@Configuration
public class AiConfig {

    @Value("classpath:prompts/diary-system.txt")
    private Resource systemPromptResource;

    /**
     * Spring AI가 자동 구성한 Builder로 ChatClient 빈을 생성한다.
     * defaultSystem으로 일기 대필 페르소나를 등록하여 모든 호출에 재사용한다.
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) throws IOException {
        String systemPrompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
        return builder.defaultSystem(systemPrompt).build();
    }
}
