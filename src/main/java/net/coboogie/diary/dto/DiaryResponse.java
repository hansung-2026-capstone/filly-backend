package net.coboogie.diary.dto;

import net.coboogie.vo.DiaryEntryVO;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 일기 단건 응답 DTO.
 * <p>
 * 저장·조회·수정 등 일기 관련 응답에서 공통으로 사용한다.
 * {@link DiaryEntryVO}를 {@code from()} 팩토리 메서드로 변환하여 생성한다.
 *
 * @param id          일기 ID
 * @param rawContent  텍스트 본문
 * @param emoji       이모지
 * @param starRating  별점 (1~5, 미설정 시 null)
 * @param writtenAt   작성 날짜
 * @param mode        일기 모드
 * @param createdAt   최초 생성 시각
 * @param updatedAt   마지막 수정 시각 (없으면 null)
 */
public record DiaryResponse(
        Long id,
        String rawContent,
        String emoji,
        Integer starRating,
        LocalDate writtenAt,
        DiaryEntryVO.Mode mode,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /**
     * {@link DiaryEntryVO} 엔티티를 응답 DTO로 변환한다.
     *
     * @param diary 변환할 일기 엔티티
     * @return 변환된 응답 DTO
     */
    public static DiaryResponse from(DiaryEntryVO diary) {
        return new DiaryResponse(
                diary.getId(),
                diary.getRawContent(),
                diary.getEmoji(),
                diary.getStarRating(),
                diary.getWrittenAt(),
                diary.getMode(),
                diary.getCreatedAt(),
                diary.getUpdatedAt()
        );
    }
}
