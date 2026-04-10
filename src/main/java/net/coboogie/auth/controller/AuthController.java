package net.coboogie.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import net.coboogie.auth.dto.TokenResponse;
import net.coboogie.auth.util.JwtTokenProvider;
import net.coboogie.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 관련 REST API 컨트롤러.
 * Base URL: {@code /api/v1/auth}
 * <p>
 * Swagger UI에서 로그인 후 토큰을 확인하거나 재발급하는 용도로 사용한다.
 * 로그인 흐름: {@code /home.html} → OAuth2 소셜 로그인 → accessToken 복사 → Swagger Authorize에 입력
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증 관련 API")
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 현재 인증된 사용자의 accessToken, refreshToken을 새로 발급하여 반환한다.
     * <p>
     * Swagger UI에서 accessToken을 Authorize에 입력한 뒤 이 엔드포인트를 호출하면
     * 토큰 쌍을 확인하거나 갱신할 수 있다.
     *
     * @param userId JWT에서 추출한 인증 사용자 ID
     * @return 새로 발급된 accessToken, refreshToken
     */
    @GetMapping("/token")
    @Operation(
            summary = "토큰 재발급",
            description = "현재 인증된 사용자의 accessToken, refreshToken을 새로 발급합니다. " +
                    "Swagger 테스트 시 home.html에서 로그인 후 accessToken을 Authorize에 입력하고 이 API를 호출하세요."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "토큰 발급 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ResponseEntity<ApiResponse<TokenResponse>> reissueToken(
            @AuthenticationPrincipal Long userId
    ) {
        String accessToken = jwtTokenProvider.generateAccessToken(userId);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userId);
        return ResponseEntity.ok(ApiResponse.ok(new TokenResponse(accessToken, refreshToken)));
    }
}
