package net.coboogie.archive.dto;

import net.coboogie.vo.ArchiveFolderVO;

import java.time.LocalDateTime;

/**
 * 아카이브 폴더 응답 DTO.
 *
 * @param id         폴더 ID
 * @param name       폴더 이름
 * @param color      폴더 색상
 * @param diaryCount 폴더 내 일기 수
 * @param createdAt  생성 시각
 */
public record ArchiveFolderResponse(
        Long id,
        String name,
        String color,
        long diaryCount,
        LocalDateTime createdAt
) {

    /** {@link ArchiveFolderVO}를 일기 수 없이 응답 DTO로 변환한다. */
    public static ArchiveFolderResponse from(ArchiveFolderVO vo, long diaryCount) {
        return new ArchiveFolderResponse(
                vo.getId(),
                vo.getName(),
                vo.getColor(),
                diaryCount,
                vo.getCreatedAt()
        );
    }
}
