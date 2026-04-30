package net.coboogie.auth.config;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Jackson ObjectMapper 설정.
 * 기본 빈은 API 응답용 camelCase, aiObjectMapper는 AI 응답 파싱용 snake_case.
 */
@Configuration
public class JacksonConfig {

    @Primary
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /** AI 응답(snake_case JSON) 파싱 전용 ObjectMapper. */
    @Bean
    @Qualifier("aiObjectMapper")
    public ObjectMapper aiObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
        return mapper;
    }
}
