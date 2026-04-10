package net.coboogie.diary.dto;

import lombok.Builder;
import net.coboogie.fillybackend.vo.DiaryEntryVO;

import java.time.LocalDate;

/**
 * 일기 저장 서비스 레이어 커맨드 객체.
 * <p>
 * 컨트롤러에서 {@code @AuthenticationPrincipal}로 추출한 {@code userId}와
 * 요청 바디({@link DiarySaveRequest})를 합쳐 서비스에 전달하는 내부 DTO이다.
 *
 * @param userId     JWT 인증 사용자 ID (DB PK)
 * @param rawContent 텍스트 본문
 * @param emoji      선택 이모지
 * @param writtenAt  일기 작성 날짜
 * @param mode       일기 모드
 */
@Builder
public record DiarySaveCommand(
        Long userId,
        String rawContent,
        String emoji,
        LocalDate writtenAt,
        DiaryEntryVO.Mode mode
) {}
