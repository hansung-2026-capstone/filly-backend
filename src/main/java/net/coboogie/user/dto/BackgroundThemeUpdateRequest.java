package net.coboogie.user.dto;

/**
 * 사용자 배경화면 테마 수정 요청 DTO.
 *
 * @param backgroundTheme 변경할 배경 테마 식별자 (필수)
 */
public record BackgroundThemeUpdateRequest(String backgroundTheme) {
}
