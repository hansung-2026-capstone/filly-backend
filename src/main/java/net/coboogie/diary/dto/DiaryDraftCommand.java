package net.coboogie.diary.dto;

import lombok.Builder;
import net.coboogie.vo.DiaryEntryVO;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code POST /api/v1/diaries/draft} 요청을 서비스 레이어로 전달하기 위한 커맨드 객체.
 * <p>
 * 컨트롤러에서 {@code @RequestPart} 파라미터들을 조합하여 생성하며,
 * 텍스트·이미지·음성 중 하나 이상이 반드시 존재해야 한다.
 */
@Builder
public record DiaryDraftCommand(
        /** 요청한 사용자의 DB PK */
        Long userId,
        /** 사용자가 직접 입력한 텍스트 메모 (선택) */
        String textContent,
        /** 첨부 이미지 파일 목록 (선택) */
        List<MultipartFile> images,
        /** 첨부 음성 파일 (선택) */
        MultipartFile voice,
        /** 일기 작성 날짜 */
        LocalDate writtenAt,
        /** 일기 작성 모드 (DEFAULT / IMAGE / IMAGE_TEXT / AI_IMAGE / AI) */
        DiaryEntryVO.Mode mode
) {
}
