package net.coboogie.common.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
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
    private Resource diarySystemPrompt;

    @Value("classpath:prompts/persona-system.txt")
    private Resource personaSystemPrompt;

    /** 일기 초안 생성용 ChatClient. */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) throws IOException {
        String systemPrompt = diarySystemPrompt.getContentAsString(StandardCharsets.UTF_8);
        return builder.defaultSystem(systemPrompt).build();
    }

    /** 페르소나 생성 전용 ChatClient. */
    @Bean
    @Qualifier("personaChatClient")
    public ChatClient personaChatClient(ChatClient.Builder builder) throws IOException {
        String systemPrompt = personaSystemPrompt.getContentAsString(StandardCharsets.UTF_8);
        return builder.defaultSystem(systemPrompt).build();
    }
}
