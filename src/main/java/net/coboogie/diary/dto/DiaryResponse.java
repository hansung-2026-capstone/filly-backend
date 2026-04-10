package net.coboogie.diary.dto;

import net.coboogie.vo.DiaryEntryVO;
import net.coboogie.vo.DiaryMediaVO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

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
 * @param mediaUrls   첨부 미디어 GCS URL 목록 (없으면 빈 리스트)
 */
public record DiaryResponse(
        Long id,
        String rawContent,
        String emoji,
        Integer starRating,
        LocalDate writtenAt,
        DiaryEntryVO.Mode mode,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<String> mediaUrls
) {
    /**
     * {@link DiaryEntryVO} 엔티티를 응답 DTO로 변환한다.
     * 연관된 {@link DiaryMediaVO} 목록에서 GCS URL을 추출하여 {@code mediaUrls}를 채운다.
     *
     * @param diary 변환할 일기 엔티티
     * @return 변환된 응답 DTO
     */
    public static DiaryResponse from(DiaryEntryVO diary) {
        List<String> mediaUrls = diary.getMedia() == null ? Collections.emptyList()
                : diary.getMedia().stream().map(DiaryMediaVO::getGcsUrl).toList();

        return new DiaryResponse(
                diary.getId(),
                diary.getRawContent(),
                diary.getEmoji(),
                diary.getStarRating(),
                diary.getWrittenAt(),
                diary.getMode(),
                diary.getCreatedAt(),
                diary.getUpdatedAt(),
                mediaUrls
        );
    }
}
