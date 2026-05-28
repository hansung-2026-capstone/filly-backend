package net.coboogie.avatar.dto;

/**
 * 아바타 생성 결과 응답 DTO.
 *
 * @param avatarHistoryId 생성된 아바타 이력 ID
 * @param avatarUrl       아바타 이미지 공개 URL
 */
public record AvatarResponse(
        Long avatarHistoryId,
        String avatarUrl
) {
}
