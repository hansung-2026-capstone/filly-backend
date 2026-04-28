package net.coboogie.stat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import net.coboogie.common.response.ApiResponse;
import net.coboogie.stat.dto.MonthlyStatResponse;
import net.coboogie.stat.service.StatService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 통계 REST API 컨트롤러.
 * Base URL: {@code /api/v1/stats}
 */
@RestController
@RequestMapping("/v1/stats")
@RequiredArgsConstructor
@Tag(name = "Stats", description = "월별 통계 API")
@SecurityRequirement(name = "bearerAuth")
public class StatController {

    private final StatService statService;

    /**
     * 월별 통계 조회 API.
     * <p>
     * 캐시된 데이터가 없으면 즉시 집계 후 반환한다.
     *
     * @param userId JWT에서 추출한 인증 사용자 ID
     * @param year   조회 연도
     * @param month  조회 월 (1~12)
     * @return 월별 통계 데이터
     */
    @GetMapping("/monthly")
    @Operation(
            summary = "월별 통계 조회",
            description = "감정 분포, IAB 키워드, 인물, 행동 패턴 등 월별 집계 데이터를 반환합니다. 캐시 미존재 시 즉시 집계합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ResponseEntity<ApiResponse<MonthlyStatResponse>> getMonthlyStats(
            @AuthenticationPrincipal Long userId,
            @RequestParam int year,
            @RequestParam int month
    ) {
        return ResponseEntity.ok(ApiResponse.ok(statService.getOrCalculate(userId, year, month)));
    }
}
