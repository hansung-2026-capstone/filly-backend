package net.coboogie.avatar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import net.coboogie.avatar.dto.AvatarResponse;
import net.coboogie.avatar.service.AvatarService;
import net.coboogie.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 아바타 이미지 생성 API 컨트롤러.
 */
@Tag(name = "Avatar", description = "아바타 이미지 생성 API")
@RestController
@RequestMapping("/v1/avatars")
@RequiredArgsConstructor
public class AvatarController {

    private final AvatarService avatarService;

    /**
     * 아바타 이미지 생성 API.
     * <p>
     * 사용자의 최신 페르소나를 기반으로 아바타 이미지를 생성하고,
     * GCS에 업로드한 뒤 서명 URL을 반환한다.
     * 이미지 생성에는 약 10~30초가 소요된다.
     *
     * @param userId JWT 인증 사용자 ID
     * @return 생성된 아바타 이력 ID 및 서명 URL
     */
    @PostMapping("/generate")
    @Operation(summary = "아바타 생성", description = "최신 페르소나 기반으로 FLUX 이미지를 생성하고 GCS에 저장한다. 약 10~30초 소요.")
    public ResponseEntity<ApiResponse<AvatarResponse>> generateAvatar(
            @AuthenticationPrincipal Long userId) {
        AvatarResponse result = avatarService.generateAvatar(userId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
