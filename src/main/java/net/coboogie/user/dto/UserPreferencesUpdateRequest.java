package net.coboogie.user.dto;

/**
 * 사용자 개인화 설정 수정 요청 DTO.
 *
 * @param gender      성별 (male, female, none)
 * @param ageGroup    나이대 (10대, 20대, 30대, 40대, 50대, 60대, 70대 이상, none)
 * @param aiDraftTone AI 초안 어투 (calm, warm, lively, literary, reflective, none)
 */
public record UserPreferencesUpdateRequest(
        String gender,
        String ageGroup,
        String aiDraftTone
) {
}
