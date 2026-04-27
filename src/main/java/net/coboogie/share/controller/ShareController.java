package net.coboogie.share.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import net.coboogie.common.response.ApiResponse;
import net.coboogie.share.dto.IdCardResponse;
import net.coboogie.share.dto.ReceiptResponse;
import net.coboogie.share.service.ShareService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공유 콘텐츠 REST API 컨트롤러.
 * Base URL: {@code /api/v1/share}
 * <p>
 * ID 카드, 영수증 등 SNS 공유용 데이터를 반환한다.
 */
@RestController
@RequestMapping("/v1/share")
@RequiredArgsConstructor
@Tag(name = "Share", description = "공유 콘텐츠 API")
@SecurityRequirement(name = "bearerAuth")
public class ShareController {

    private final ShareService shareService;

    /**
     * ID 카드 공유 데이터 조회 API.
     * <p>
     * 아바타 URL, 닉네임, IAB 키워드 목록(최대 10개)을 반환한다.
     *
     * @param userId JWT에서 추출한 인증 사용자 ID
     * @return ID 카드 데이터
     */
    @GetMapping("/id-card")
    @Operation(
            summary = "ID 카드 조회",
            description = "아바타 URL, 닉네임, 취향 키워드(최대 10개)를 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ResponseEntity<ApiResponse<IdCardResponse>> getIdCard(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(shareService.getIdCard(userId)));
    }

    /**
     * 월별 영수증 공유 데이터 조회 API.
     * <p>
     * 주문번호(FL-YYYYMMDD-NNN), 일기 개수, 총 글자 수, 감정 분포, 페르소나 제목을 반환한다.
     *
     * @param userId JWT에서 추출한 인증 사용자 ID
     * @param year   조회 연도
     * @param month  조회 월 (1~12)
     * @return 영수증 데이터
     */
    @GetMapping("/receipt")
    @Operation(
            summary = "월별 영수증 조회",
            description = "일기 개수, 총 글자 수, 감정 분포, 페르소나 제목을 영수증 형식으로 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ResponseEntity<ApiResponse<ReceiptResponse>> getReceipt(
            @AuthenticationPrincipal Long userId,
            @RequestParam int year,
            @RequestParam int month
    ) {
        return ResponseEntity.ok(ApiResponse.ok(shareService.getReceipt(userId, year, month)));
    }
}
