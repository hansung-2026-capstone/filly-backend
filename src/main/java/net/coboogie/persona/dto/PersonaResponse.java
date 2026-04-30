package net.coboogie.persona.dto;

import net.coboogie.vo.PersonaSnapshotVO;

import java.time.LocalDateTime;

/**
 * 페르소나 단건 응답 DTO.
 *
 * @param id          페르소나 ID
 * @param title       페르소나 제목
 * @param summary     페르소나 내용
 * @param generatedAt 생성 시각
 */
public record PersonaResponse(
        Long id,
        String title,
        String summary,
        LocalDateTime generatedAt
) {
    /** {@link PersonaSnapshotVO}를 응답 DTO로 변환한다. */
    public static PersonaResponse from(PersonaSnapshotVO vo) {
        return new PersonaResponse(vo.getId(), vo.getTitle(), vo.getSummary(), vo.getGeneratedAt());
    }
}
