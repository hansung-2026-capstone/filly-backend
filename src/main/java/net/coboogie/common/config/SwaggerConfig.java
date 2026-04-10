package net.coboogie.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI (springdoc-openapi) 설정.
 * <p>
 * 접속 경로: /swagger-ui/index.html
 * JWT Bearer 인증 스킴을 전역으로 적용하여, Swagger UI에서 토큰을 한 번만 입력하면
 * 모든 인증 필요 API를 바로 테스트할 수 있다.
 * 이때 토큰은 accessToken의 내용을 붙여넣으면 된다.
 */
@Configuration
public class SwaggerConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        // Authorization: Bearer <token> 형식의 JWT 인증 스킴 정의
        SecurityScheme securityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization");

        return new OpenAPI()
                .info(new Info()
                        .title("Filly Backend API")
                        .description("AI 기반 페르소나 분석 다이어리 서비스 Filly의 백엔드 API")
                        .version("v1.0.0"))
                        .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, securityScheme))
                // 모든 API에 JWT 인증을 기본 요구사항으로 등록
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
