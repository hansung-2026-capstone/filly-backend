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

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

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
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                        .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/oauth2/**", "/login/**",            // OAuth2, 로그인
                                "/swagger-ui/**", "/swagger-ui.html", // Swagger UI
                                "/v3/api-docs/**",
                                "/*.html", "/css/**", "/js/**"        // 정적 HTML 페이지
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo ->
                        userInfo.userService(customOAuth2UserService))
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
