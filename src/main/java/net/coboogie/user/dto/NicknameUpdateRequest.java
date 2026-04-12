package net.coboogie.user.dto;

/**
 * 사용자 닉네임 수정 요청 DTO.
 *
 * @param nickname 변경할 닉네임 (필수)
 */
public record NicknameUpdateRequest(String nickname) {
}
