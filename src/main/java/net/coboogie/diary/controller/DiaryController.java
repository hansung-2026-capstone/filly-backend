package net.coboogie.diary.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import net.coboogie.common.response.ApiResponse;
import net.coboogie.diary.dto.DiaryDraftCommand;
import net.coboogie.diary.dto.DiaryDraftResponse;
import net.coboogie.diary.dto.DiaryResponse;
import net.coboogie.diary.dto.DiarySaveCommand;
import net.coboogie.diary.dto.DiaryUpdateRequest;
import net.coboogie.diary.service.DiaryService;
import net.coboogie.vo.DiaryEntryVO;
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
@SecurityRequirement(name = "bearerAuth")
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

    /**
     * 월별 일기 목록 조회 API.
     * <p>
     * year, month 쿼리 파라미터로 조회 월을 지정한다. 본인 일기만 반환된다.
     *
     * @param userId JWT에서 추출한 인증 사용자 ID
     * @param year   조회 연도 (예: 2026)
     * @param month  조회 월 (1~12)
     * @return 해당 월의 일기 목록 (작성일 오름차순)
     */
    @GetMapping
    @Operation(
            summary = "월별 일기 목록 조회",
            description = "year, month 파라미터로 해당 월의 일기 목록을 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ResponseEntity<ApiResponse<List<DiaryResponse>>> getDiariesByMonth(
            @AuthenticationPrincipal Long userId,
            @RequestParam int year,
            @RequestParam int month
    ) {
        List<DiaryResponse> responses = diaryService.getDiariesByMonth(userId, year, month);
        return ResponseEntity.ok(ApiResponse.ok(responses));
    }

    /**
     * 일기 단건 조회 API.
     * <p>
     * 본인 소유의 일기만 조회할 수 있다.
     *
     * @param userId JWT에서 추출한 인증 사용자 ID
     * @param id     조회할 일기 ID
     * @return 조회된 일기 정보
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "일기 단건 조회",
            description = "일기 ID로 단건 조회합니다. 본인 소유의 일기만 조회 가능합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "일기 없음")
    })
    public ResponseEntity<ApiResponse<DiaryResponse>> getDiary(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        DiaryResponse response = diaryService.getDiary(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 일기 저장 API.
     * <p>
     * Multipart 형식으로 일기 내용 및 이미지를 받아 DB에 저장한다.
     * <ul>
     *   <li>DEFAULT: rawContent(텍스트)만 저장</li>
     *   <li>IMAGE: 이미지를 GCS에 업로드하고 diary_media에 저장</li>
     *   <li>IMAGE_TEXT: 텍스트 + 이미지 모두 저장</li>
     * </ul>
     *
     * @param userId     JWT에서 추출한 인증 사용자 ID
     * @param rawContent 텍스트 본문 (DEFAULT/IMAGE_TEXT 모드, 선택)
     * @param emoji      이모지 (선택)
     * @param writtenAt  작성 날짜 (ISO 형식: yyyy-MM-dd)
     * @param mode       일기 모드 (DEFAULT / IMAGE / IMAGE_TEXT / AI_IMAGE / AI)
     * @param images     첨부 이미지 파일 목록 (IMAGE/IMAGE_TEXT 모드, 선택)
     * @return 저장된 일기 정보 (mediaUrls 포함)
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "일기 저장",
            description = "일기를 DB에 저장합니다. IMAGE/IMAGE_TEXT 모드는 이미지를 GCS에 업로드하고 diary_media에 저장합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "저장 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ResponseEntity<ApiResponse<DiaryResponse>> saveDiary(
            @AuthenticationPrincipal Long userId,
            @RequestPart(required = false) String rawContent,
            @RequestPart(required = false) String emoji,
            @RequestPart String writtenAt,
            @RequestPart String mode,
            @RequestPart(required = false) List<MultipartFile> images
    ) {
        DiarySaveCommand command = DiarySaveCommand.builder()
                .userId(userId)
                .rawContent(rawContent)
                .emoji(emoji)
                .writtenAt(LocalDate.parse(writtenAt))
                .mode(DiaryEntryVO.Mode.valueOf(mode))
                .images(images)
                .build();

        DiaryResponse response = diaryService.saveDiary(command);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 일기 수정 API.
     * <p>
     * rawContent, emoji를 수정한다. null인 필드는 기존 값을 유지한다.
     * 본인 소유의 일기만 수정 가능하다.
     *
     * @param userId  JWT에서 추출한 인증 사용자 ID
     * @param id      수정할 일기 ID
     * @param request 수정할 rawContent, emoji
     * @return 수정된 일기 정보
     */
    @PutMapping("/{id}")
    @Operation(
            summary = "일기 수정",
            description = "rawContent, emoji를 수정합니다. null인 필드는 기존 값을 유지합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "일기 없음")
    })
    public ResponseEntity<ApiResponse<DiaryResponse>> updateDiary(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestBody DiaryUpdateRequest request
    ) {
        DiaryResponse response = diaryService.updateDiary(id, userId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 일기 삭제 API.
     * <p>
     * 본인 소유의 일기만 삭제 가능하다.
     *
     * @param userId JWT에서 추출한 인증 사용자 ID
     * @param id     삭제할 일기 ID
     * @return 데이터 없는 성공 응답
     */
    @DeleteMapping("/{id}")
    @Operation(
            summary = "일기 삭제",
            description = "일기를 삭제합니다. 본인 소유의 일기만 삭제 가능합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "일기 없음")
    })
    public ResponseEntity<ApiResponse<Void>> deleteDiary(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        diaryService.deleteDiary(id, userId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
