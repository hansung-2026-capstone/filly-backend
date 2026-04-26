package net.coboogie.auth.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import net.coboogie.auth.dto.CustomOAuth2User;
import net.coboogie.auth.util.JwtTokenProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * OAuth2 소셜 로그인 성공 핸들러.
 * <p>
 * 인증 성공 시 JWT를 생성하고 프론트엔드 홈({@code https://filly-diary.com/})으로 리다이렉트한다.
 * accessToken과 refreshToken을 쿼리 파라미터로 전달하면,
 * 클라이언트가 이를 localStorage에 저장하여 이후 API 호출에 사용한다.
 */
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;

    private static final String FRONTEND_REDIRECT_URL = "https://filly-diary.com/";

    /**
     * 로그인 성공 시 호출된다.
     * JWT를 생성한 뒤 {@code https://filly-diary.com/?accessToken=...&refreshToken=...}으로 리다이렉트한다.
     * <p>
     * Firebase CDN 등 중간 레이어가 이 응답을 캐싱하면 다른 사용자가 이전 사용자의 토큰을 받는
     * 보안 문제가 발생하므로, Cache-Control: no-store 헤더를 명시적으로 설정한다.
     *
     * @param request        HTTP 요청
     * @param response       HTTP 응답
     * @param authentication 인증 객체 (CustomOAuth2User 포함)
     * @throws IOException 리다이렉트 실패 시
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        CustomOAuth2User customUser = (CustomOAuth2User) authentication.getPrincipal();
        Long userId = customUser.getUserId();

        String accessToken = jwtTokenProvider.generateAccessToken(userId);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userId);

        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        System.out.println("[OAuth2SuccessHandler] userId=" + userId + " 로그인 성공");
        response.sendRedirect(FRONTEND_REDIRECT_URL + "?accessToken=" + accessToken + "&refreshToken=" + refreshToken);
    }
}
