package net.coboogie.auth.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;

import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/**
 * OAuth2 인증 요청(state, redirect_uri 등)을 세션 대신 쿠키에 저장하는 구현체.
 * <p>
 * Cloud Run 등 멀티 인스턴스 환경에서는 인스턴스 간 세션이 공유되지 않아
 * 기본 {@code HttpSessionOAuth2AuthorizationRequestRepository}가 state 검증에 실패한다.
 * 쿠키를 사용하면 브라우저가 동일한 값을 콜백 요청에 다시 전송하므로 인스턴스에 무관하게 검증할 수 있다.
 */
@Component
public class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

//    private static final String COOKIE_NAME = "oauth2_auth_request";
    private static final String COOKIE_NAME = "__session";
    private static final int COOKIE_MAX_AGE_SECONDS = 180; // 3분

    /**
     * 쿠키에서 OAuth2 인증 요청을 로드한다.
     */
    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
//        return findCookie(request, COOKIE_NAME)
//                .map(cookie -> deserialize(cookie.getValue()))
//                .orElse(null);

        // 1. 요청에 포함된 모든 쿠키 이름을 로그로 찍어봅니다.
        if (request.getCookies() != null) {
            System.out.println("--- Incoming Cookies Start ---");
            for (Cookie c : request.getCookies()) {
                System.out.println("Cookie Name: " + c.getName() + ", Path: " + c.getPath());
            }
            System.out.println("--- Incoming Cookies End ---");
        } else {
            System.out.println("No Cookies found in request!");
        }

        return findCookie(request, COOKIE_NAME)
                .map(cookie -> deserialize(cookie.getValue()))
                .orElse(null);
    }

    /**
     * OAuth2 인증 요청을 쿠키에 저장한다.
     * {@code authorizationRequest}가 null이면 쿠키를 삭제한다.
     */
    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        if (authorizationRequest == null) {
            deleteCookie(request, response, COOKIE_NAME);
            return;
        }
//        Cookie cookie = new Cookie(COOKIE_NAME, serialize(authorizationRequest));
//        cookie.setPath("/");
//        cookie.setHttpOnly(true);
//        cookie.setSecure(true);
//        cookie.setMaxAge(COOKIE_MAX_AGE_SECONDS);
//        response.addHeader("Set-Cookie", String.format("%s=%s; Max-Age=%d; Path=/; HttpOnly; Secure; SameSite=None",
//                COOKIE_NAME, serialize(authorizationRequest), COOKIE_MAX_AGE_SECONDS));

        String serializedRequest = serialize(authorizationRequest);

        // 1. SameSite=None과 Secure는 세트입니다. (하나라도 빠지면 브라우저가 쿠키를 거부함)
        // 2. Path=/ 로 설정해야 /api 경로에서도, 그 외 경로에서도 쿠키를 읽을 수 있습니다.
        String cookieValue = String.format(
                "%s=%s; Max-Age=%d; Path=/; HttpOnly; Secure; SameSite=None",
                COOKIE_NAME,
                serializedRequest,
                COOKIE_MAX_AGE_SECONDS
        );

        // 기존의 response.addCookie(cookie)는 지우고, 헤더만 깔끔하게 추가합니다.
        response.addHeader("Set-Cookie", cookieValue);
    }

    /**
     * 쿠키에서 OAuth2 인증 요청을 꺼내고 쿠키를 삭제한다.
     */
    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                  HttpServletResponse response) {
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        if (authorizationRequest != null) {
            deleteCookie(request, response, COOKIE_NAME);
        }
        return authorizationRequest;
    }

    private String serialize(OAuth2AuthorizationRequest request) {
        return Base64.getUrlEncoder().encodeToString(SerializationUtils.serialize(request));
    }

    private OAuth2AuthorizationRequest deserialize(String value) {
        return (OAuth2AuthorizationRequest) SerializationUtils.deserialize(
                Base64.getUrlDecoder().decode(value));
    }

    private Optional<Cookie> findCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(c -> name.equals(c.getName()))
                .findFirst();
    }

    private void deleteCookie(HttpServletRequest request, HttpServletResponse response, String name) {
        if (request.getCookies() == null) {
            return;
        }
        Arrays.stream(request.getCookies())
                .filter(c -> name.equals(c.getName()))
                .forEach(c -> {
                    response.addHeader("Set-Cookie", String.format(
                            "%s=; Max-Age=0; Path=/; HttpOnly; Secure; SameSite=None", name));
                });
    }
}
