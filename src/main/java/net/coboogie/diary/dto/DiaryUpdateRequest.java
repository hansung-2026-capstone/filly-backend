package net.coboogie.diary.dto;

/**
 * 일기 수정 요청 DTO.
 * <p>
 * PUT /api/v1/diaries/{id} 요청 바디로 사용된다.
 * rawContent와 emoji만 수정 가능하며, null인 필드는 수정하지 않는다.
 *
 * @param rawContent 수정할 텍스트 본문 (null이면 변경하지 않음)
 * @param emoji      수정할 이모지 (null이면 변경하지 않음)
 */
public record DiaryUpdateRequest(
        String rawContent,
        String emoji
) {}
