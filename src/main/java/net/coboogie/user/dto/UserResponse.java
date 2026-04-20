package net.coboogie.user.dto;

import net.coboogie.vo.UserVO;

import java.time.LocalDateTime;

/**
 * 사용자 정보 응답 DTO.
 *
 * @param id               사용자 DB PK
 * @param nickname         닉네임
 * @param currentAvatarUrl 현재 아바타 이미지 URL
 * @param currentBgUrl     현재 배경 이미지 URL
 * @param createdAt        가입 일시
 */
public record UserResponse(
        Long id,
        String nickname,
        String currentAvatarUrl,
        String currentBgUrl,
        String backgroundTheme,
        LocalDateTime createdAt
) {
    public static UserResponse from(UserVO user) {
        return new UserResponse(
                user.getId(),
                user.getNickname(),
                user.getCurrentAvatarUrl(),
                user.getCurrentBgUrl(),
                user.getBackgroundTheme(),
                user.getCreatedAt()
        );
    }
}
