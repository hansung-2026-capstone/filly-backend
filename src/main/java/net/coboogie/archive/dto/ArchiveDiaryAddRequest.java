package net.coboogie.archive.dto;

/**
 * 폴더에 일기 추가 요청 DTO.
 *
 * @param diaryId 추가할 일기 ID
 */
public record ArchiveDiaryAddRequest(Long diaryId) {
}
