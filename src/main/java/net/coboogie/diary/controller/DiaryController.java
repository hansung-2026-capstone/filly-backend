package net.coboogie.diary.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import net.coboogie.common.response.ApiResponse;
import net.coboogie.diary.dto.DiaryDraftCommand;
import net.coboogie.diary.dto.DiaryDraftResponse;
import net.coboogie.diary.service.DiaryService;
import net.coboogie.fillybackend.vo.DiaryEntryVO;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * 일기 관련 REST API 컨트롤러.
 * Base URL: {@code /api/v1/diaries}
 * <p>
 * 모든 요청은 JWT 인증이 필요하며, {@code Authorization: Bearer <token>} 헤더를 포함해야 한다.
 * {@code @AuthenticationPrincipal}로 추출되는 값은 {@code JwtAuthenticationFilter}에서
 * SecurityContext에 저장한 {@code userId} (DB PK, Long 타입)이다.
 */
@RestController
@RequestMapping("/api/v1/diaries")
@RequiredArgsConstructor
@Tag(name = "Diary", description = "일기 관련 API")
public class DiaryController {

    private final DiaryService diaryService;

    /**
     * AI 일기 초안 생성 API.
     * <p>
     * 텍스트·이미지·음성을 Multipart 형식으로 받아 AI 일기 초안을 생성한다.
     * 초안은 DB에 저장되지 않으므로, 사용자가 확인 후 {@code POST /api/v1/diaries}로 최종 저장해야 한다.
     *
     * @param userId    JWT에서 추출한 인증 사용자 ID
     * @param content   텍스트 메모 (선택)
     * @param images    이미지 파일 목록 (선택)
     * @param voice     음성 파일 (선택)
     * @param writtenAt 작성 날짜 (ISO 형식: yyyy-MM-dd)
     * @param mode      일기 모드 (DEFAULT / IMAGE / IMAGE_TEXT / AI_IMAGE / AI)
     */
    @PostMapping(value = "/draft", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "AI 일기 초안 생성",
            description = "텍스트, 이미지, 음성을 받아 AI가 일기 초안을 생성합니다. " +
                    "세 가지 입력 중 하나 이상 필요합니다. 초안 확인 후 POST /api/v1/diaries로 최종 저장하세요."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "초안 생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ResponseEntity<ApiResponse<DiaryDraftResponse>> createDraft(
            @AuthenticationPrincipal Long userId,
            @RequestPart(required = false) String content,
            @RequestPart(required = false) List<MultipartFile> images,
            @RequestPart(required = false) MultipartFile voice,
            @RequestPart String writtenAt,
            @RequestPart String mode
    ) {
        DiaryDraftCommand command = DiaryDraftCommand.builder()
                .userId(userId)
                .textContent(content)
                .images(images)
                .voice(voice)
                .writtenAt(LocalDate.parse(writtenAt))
                .mode(DiaryEntryVO.Mode.valueOf(mode))
                .build();

        DiaryDraftResponse response = diaryService.createDraft(command);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
