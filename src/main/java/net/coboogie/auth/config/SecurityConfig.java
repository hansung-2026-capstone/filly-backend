package net.coboogie.auth.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import net.coboogie.auth.filter.JwtAuthenticationFilter;
import net.coboogie.auth.handler.OAuth2FailureHandler;
import net.coboogie.auth.handler.OAuth2SuccessHandler;
import net.coboogie.auth.service.CustomOAuth2UserService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.ForwardedHeaderFilter;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CookieOAuth2AuthorizationRequestRepository cookieOAuth2AuthorizationRequestRepository;

    /**
     * JwtAuthenticationFilter는 @Component로 등록되어 있어 Spring Boot가 서블릿 필터로도
     * 자동 등록한다. 이중 등록 시 OncePerRequestFilter의 "이미 실행됨" 체크로 인해
     * Security 체인 안에서 필터가 스킵될 수 있으므로 서블릿 필터 등록을 비활성화한다.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }

    /**
     * 프론트엔드(filly-diary.com)에서의 API 호출을 허용하는 CORS 설정.
     * JWT는 Authorization 헤더로 전달되므로 해당 헤더를 허용한다.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("https://filly-diary.com"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // state를 쿠키에 저장하므로 세션 불필요 — STATELESS 복원.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                        .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                // Spring Security 필터 경로(/oauth2/**, /login/**)는 MVC 핸들러가 없으므로
                                // MvcRequestMatcher 대신 PathPatternRequestMatcher를 명시적으로 사용한다.
                                PathPatternRequestMatcher.pathPattern("/oauth2/**"),
                                PathPatternRequestMatcher.pathPattern("/login/**"),
                                PathPatternRequestMatcher.pathPattern("/v1/auth/refresh"),
                                PathPatternRequestMatcher.pathPattern("/swagger-ui/**"),
                                PathPatternRequestMatcher.pathPattern("/swagger-ui.html"),
                                PathPatternRequestMatcher.pathPattern("/v3/api-docs/**"),
                                PathPatternRequestMatcher.pathPattern("/*.html"),
                                PathPatternRequestMatcher.pathPattern("/css/**"),
                                PathPatternRequestMatcher.pathPattern("/js/**")
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(authorization -> authorization
                                .baseUri("/oauth2/authorization")
                                .authorizationRequestRepository(cookieOAuth2AuthorizationRequestRepository))
                        .redirectionEndpoint(redirection -> redirection
                                .baseUri("/login/oauth2/code/*")
                        )
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler(oAuth2FailureHandler)
                )
                .exceptionHandling(exceptions -> exceptions
                        // oauth2Login() 기본 EntryPoint(→ /login 리다이렉트)를 덮어써서
                        // JWT 인증 실패 시 302가 아닌 401 JSON을 반환한다.
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"success\":false,\"message\":\"인증이 필요합니다. 토큰을 확인해 주세요.\"}");
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
