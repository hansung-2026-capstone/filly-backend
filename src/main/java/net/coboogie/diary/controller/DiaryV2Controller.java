package net.coboogie.diary.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import net.coboogie.common.response.ApiResponse;
import net.coboogie.diary.dto.DiaryDraftCommand;
import net.coboogie.diary.dto.DiaryDraftResponse;
import net.coboogie.diary.service.DiaryService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * 일기 v2 REST API 컨트롤러.
 * Base URL: {@code /api/v2/diaries}
 */
@RestController
@RequestMapping("/v2/diaries")
@RequiredArgsConstructor
@Tag(name = "Diary V2", description = "일기 v2 API")
@SecurityRequirement(name = "bearerAuth")
public class DiaryV2Controller {

    private final DiaryService diaryService;

    /**
     * AI 일기 초안 생성 API v2.
     * STT/BLIP 전처리 없이 텍스트·이미지·음성을 Gemini 멀티모달 입력으로 전달한다.
     */
    @PostMapping(value = "/draft", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "AI 일기 초안 생성 v2",
            description = "텍스트, 최대 4장 이미지, 최대 60초 음성을 받아 Gemini 멀티모달 입력으로 일기 초안을 생성합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "초안 생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 입력값"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ResponseEntity<ApiResponse<DiaryDraftResponse>> createDraft(
            @AuthenticationPrincipal Long userId,
            @RequestPart(required = false) String content,
            @RequestPart(required = false) List<MultipartFile> images,
            @RequestPart(required = false) MultipartFile voice,
            @RequestPart String writtenAt
    ) {
        DiaryDraftCommand command = DiaryDraftCommand.builder()
                .userId(userId)
                .textContent(content)
                .images(images)
                .voice(voice)
                .writtenAt(LocalDate.parse(writtenAt))
                .build();

        DiaryDraftResponse response = diaryService.createDraftV2(command);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
