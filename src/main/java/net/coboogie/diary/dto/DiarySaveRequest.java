package net.coboogie.diary.dto;

import net.coboogie.fillybackend.vo.DiaryEntryVO;

import java.time.LocalDate;

/**
 * 일기 저장 API의 요청 바디 DTO.
 * <p>
 * {@code POST /api/v1/diaries} 엔드포인트에서 JSON 형식으로 수신된다.
 * mode에 따라 rawContent 필요 여부가 달라진다.
 * <ul>
 *   <li>DEFAULT: rawContent 필수</li>
 *   <li>IMAGE: rawContent 불필요, mediaUrls 별도 업로드</li>
 *   <li>IMAGE_TEXT: rawContent + 미디어 모두 사용</li>
 * </ul>
 *
 * @param rawContent 사용자가 작성한 텍스트 본문 (DEFAULT/IMAGE_TEXT 모드)
 * @param emoji      선택한 이모지 (선택)
 * @param writtenAt  일기 작성 날짜 (yyyy-MM-dd)
 * @param mode       일기 모드 (DEFAULT, IMAGE, IMAGE_TEXT, AI_IMAGE, AI)
 */
public record DiarySaveRequest(
        String rawContent,
        String emoji,
        LocalDate writtenAt,
        DiaryEntryVO.Mode mode
) {}
