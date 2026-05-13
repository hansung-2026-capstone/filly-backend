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
 * @param backgroundTheme  배경화면 테마 식별자
 * @param gender           성별
 * @param ageGroup         나이대
 * @param aiDraftTone      AI 초안 어투
 * @param createdAt        가입 일시
 */
public record UserResponse(
        Long id,
        String nickname,
        String currentAvatarUrl,
        String currentBgUrl,
        String backgroundTheme,
        String gender,
        String ageGroup,
        String aiDraftTone,
        LocalDateTime createdAt
) {
    private static final String DEFAULT_PREFERENCE = "none";

    public static UserResponse from(UserVO user) {
        return from(user, user.getCurrentAvatarUrl());
    }

    public static UserResponse from(UserVO user, String currentAvatarUrl) {
        return new UserResponse(
                user.getId(),
                user.getNickname(),
                currentAvatarUrl,
                user.getCurrentBgUrl(),
                user.getBackgroundTheme(),
                defaultPreference(user.getGender()),
                defaultPreference(user.getAgeGroup()),
                defaultPreference(user.getAiDraftTone()),
                user.getCreatedAt()
        );
    }

    private static String defaultPreference(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_PREFERENCE;
        }
        return value;
    }
}
