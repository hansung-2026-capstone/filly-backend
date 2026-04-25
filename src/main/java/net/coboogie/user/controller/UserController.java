package net.coboogie.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import net.coboogie.common.response.ApiResponse;
import net.coboogie.user.dto.BackgroundThemeUpdateRequest;
import net.coboogie.user.dto.NicknameUpdateRequest;
import net.coboogie.user.dto.UserResponse;
import net.coboogie.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 사용자 관련 REST API 컨트롤러.
 * Base URL: {@code /api/v1/users}
 * <p>
 * 모든 요청은 JWT 인증이 필요하며, {@code Authorization: Bearer <token>} 헤더를 포함해야 한다.
 */
@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "사용자 관련 API")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    /**
     * 현재 인증된 사용자의 정보를 반환한다.
     *
     * @param userId JWT에서 추출한 인증 사용자 ID
     * @return 사용자 정보 (id, nickname, avatarUrl, bgUrl, createdAt)
     */
    @GetMapping("/me")
    @Operation(
            summary = "내 정보 조회",
            description = "현재 로그인한 사용자의 정보를 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ResponseEntity<ApiResponse<UserResponse>> getMe(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getMe(userId)));
    }

    /**
     * 현재 인증된 사용자의 배경화면 테마를 수정한다.
     *
     * @param userId  JWT에서 추출한 인증 사용자 ID
     * @param request 변경할 배경 테마 식별자
     * @return 데이터 없는 성공 응답
     */
    @PatchMapping("/me/background-theme")
    @Operation(
            summary = "배경화면 테마 수정",
            description = "현재 로그인한 사용자의 배경화면 테마를 수정합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "테마 값 누락 또는 공백"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ResponseEntity<ApiResponse<Void>> updateBackgroundTheme(
            @AuthenticationPrincipal Long userId,
            @RequestBody BackgroundThemeUpdateRequest request
    ) {
        userService.updateBackgroundTheme(userId, request.backgroundTheme());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * 현재 인증된 사용자의 닉네임을 수정한다.
     *
     * @param userId  JWT에서 추출한 인증 사용자 ID
     * @param request 변경할 닉네임
     * @return 데이터 없는 성공 응답
     */
    @PatchMapping("/me/nickname")
    @Operation(
            summary = "닉네임 수정",
            description = "현재 로그인한 사용자의 닉네임을 수정합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "닉네임 누락 또는 공백"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ResponseEntity<ApiResponse<Void>> updateNickname(
            @AuthenticationPrincipal Long userId,
            @RequestBody NicknameUpdateRequest request
    ) {
        userService.updateNickname(userId, request.nickname());
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
