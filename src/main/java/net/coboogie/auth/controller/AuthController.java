package net.coboogie.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import net.coboogie.auth.dto.TokenResponse;
import net.coboogie.auth.util.JwtTokenProvider;
import net.coboogie.common.response.ApiResponse;
import net.coboogie.user.repository.UserRepository;
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
    private final UserRepository userRepository;

    /**
     * refreshToken을 검증하여 새로운 accessToken과 refreshToken을 발급한다.
     * JWT 인증 없이 호출 가능하다 (SecurityConfig에서 permitAll 처리).
     * <p>
     * 토큰이 유효하더라도 해당 userId의 사용자가 DB에 없으면 401을 반환한다.
     * 계정 삭제 후 refresh token이 남아 있는 경우 무한 갱신을 막기 위함이다.
     *
     * @param request refreshToken을 담은 요청 바디
     * @return 새로 발급된 accessToken, refreshToken
     */
    @PostMapping("/refresh")
    @Operation(summary = "토큰 갱신", description = "refreshToken으로 새로운 accessToken과 refreshToken을 발급합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "토큰 갱신 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "refreshToken 만료, 유효하지 않음, 또는 사용자 없음")
    })
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @RequestBody TokenRefreshRequest request
    ) {
        if (!jwtTokenProvider.validateToken(request.refreshToken())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("refreshToken이 만료되었거나 유효하지 않습니다."));
        }
        Long userId = jwtTokenProvider.getUserIdFromToken(request.refreshToken());
        if (!userRepository.existsById(userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("존재하지 않는 사용자입니다."));
        }
        String newAccessToken = jwtTokenProvider.generateAccessToken(userId);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId);
        return ResponseEntity.ok(ApiResponse.ok(new TokenResponse(newAccessToken, newRefreshToken)));
    }

    record TokenRefreshRequest(String refreshToken) {}
}
