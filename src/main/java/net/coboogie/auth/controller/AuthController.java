package net.coboogie.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import net.coboogie.auth.dto.TokenResponse;
import net.coboogie.auth.util.JwtTokenProvider;
import net.coboogie.common.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 인증 관련 REST API 컨트롤러.
 * Base URL: {@code /api/v1/auth}
 */
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증 관련 API")
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * refreshToken을 검증하여 새로운 accessToken과 refreshToken을 발급한다.
     * JWT 인증 없이 호출 가능하다 (SecurityConfig에서 permitAll 처리).
     *
     * @param request refreshToken을 담은 요청 바디
     * @return 새로 발급된 accessToken, refreshToken
     */
    @PostMapping("/refresh")
    @Operation(summary = "토큰 갱신", description = "refreshToken으로 새로운 accessToken과 refreshToken을 발급합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "토큰 갱신 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "refreshToken 만료 또는 유효하지 않음")
    })
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @RequestBody TokenRefreshRequest request
    ) {
        if (!jwtTokenProvider.validateToken(request.refreshToken())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("refreshToken이 만료되었거나 유효하지 않습니다."));
        }
        Long userId = jwtTokenProvider.getUserIdFromToken(request.refreshToken());
        String newAccessToken = jwtTokenProvider.generateAccessToken(userId);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId);
        return ResponseEntity.ok(ApiResponse.ok(new TokenResponse(newAccessToken, newRefreshToken)));
    }

    record TokenRefreshRequest(String refreshToken) {}
}
