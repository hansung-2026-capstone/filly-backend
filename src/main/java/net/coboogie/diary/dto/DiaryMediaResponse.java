package net.coboogie.diary.dto;

import java.time.LocalDateTime;

/**
 * 일기 첨부 미디어 응답 DTO.
 *
 * @param id        미디어 ID
 * @param type      미디어 타입
 * @param url       클라이언트 접근용 URL
 * @param fileSize  파일 크기
 * @param createdAt 최초 생성 시각
 */
public record DiaryMediaResponse(
        Long id,
        String type,
        String url,
        Integer fileSize,
        LocalDateTime createdAt
) {
}
