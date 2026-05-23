package net.coboogie.recommendation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import net.coboogie.common.response.ApiResponse;
import net.coboogie.recommendation.dto.RecommendationDrawResponse;
import net.coboogie.recommendation.dto.RecommendationRevealResponse;
import net.coboogie.recommendation.dto.RecommendationShuffleResponse;
import net.coboogie.recommendation.service.RecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 뽑기형 추천 REST API 컨트롤러.
 */
@RestController
@RequestMapping("/v1/recommendations")
@RequiredArgsConstructor
@Tag(name = "Recommendation", description = "뽑기형 추천 API")
@SecurityRequirement(name = "bearerAuth")
public class RecommendationController {

    private final RecommendationService recommendationService;

    /**
     * 추천 뽑기 세션을 시작하고 카드 3장을 생성한다.
     *
     * @param userId JWT 인증 사용자 ID
     * @return 추천 뽑기 카드 뒷면 응답
     */
    @PostMapping("/draws")
    @Operation(summary = "추천 뽑기 시작", description = "사용자의 일기 분석 데이터를 기반으로 서로 다른 카테고리 카드 3장을 생성합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "추천 뽑기 생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "추천 AI 생성 실패")
    })
    public ResponseEntity<ApiResponse<RecommendationDrawResponse>> draw(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(recommendationService.draw(userId)));
    }

    /**
     * 추천 뽑기 세션 상태와 카드 목록을 조회한다.
     *
     * @param userId JWT 인증 사용자 ID
     * @param drawId 추천 뽑기 세션 ID
     * @return 추천 뽑기 상태 응답
     */
    @GetMapping("/draws/{drawId}")
    @Operation(summary = "추천 뽑기 상태 조회", description = "추천 카드 생성 상태를 조회하고 완료 시 카드 뒷면 목록을 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "추천 뽑기 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "추천 뽑기 없음")
    })
    public ResponseEntity<ApiResponse<RecommendationDrawResponse>> getDraw(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long drawId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(recommendationService.getDraw(userId, drawId)));
    }

    /**
     * 선택한 추천 카드를 공개한다.
     *
     * @param userId JWT 인증 사용자 ID
     * @param drawId 추천 뽑기 세션 ID
     * @param cardId 추천 카드 ID
     * @return 공개된 추천 카드 내용
     */
    @PostMapping("/draws/{drawId}/cards/{cardId}/reveal")
    @Operation(summary = "추천 카드 공개", description = "선택한 추천 카드의 카테고리, 콘텐츠, 추천 이유를 공개합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "추천 카드 공개 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "선택할 수 없는 카드"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "추천 카드 없음")
    })
    public ResponseEntity<ApiResponse<RecommendationRevealResponse>> reveal(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long drawId,
            @PathVariable Long cardId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(recommendationService.reveal(userId, drawId, cardId)));
    }

    /**
     * 이미 본 카드 1장을 제외하고 안 본 카드 2장과 새 카드 1장으로 다시 구성한다.
     *
     * @param userId JWT 인증 사용자 ID
     * @param drawId 추천 뽑기 세션 ID
     * @return 다시 구성된 카드 뒷면 응답
     */
    @PostMapping("/draws/{drawId}/shuffle")
    @Operation(summary = "다른 추천 보기", description = "이미 본 카드는 제외하고 안 본 카드 2장과 새 카드 1장을 반환합니다. 하루 3회 제한입니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "다른 추천 보기 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "shuffle 불가 상태"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "추천 뽑기 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "일일 shuffle 제한 초과"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "추천 AI 생성 실패")
    })
    public ResponseEntity<ApiResponse<RecommendationShuffleResponse>> shuffle(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long drawId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(recommendationService.shuffle(userId, drawId)));
    }

    /**
     * 공개한 추천 카드 이력을 조회한다.
     *
     * @param userId JWT 인증 사용자 ID
     * @return 공개된 추천 카드 이력
     */
    @GetMapping("/history")
    @Operation(summary = "추천 이력 조회", description = "사용자가 공개한 추천 카드 이력을 최신순으로 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "추천 이력 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ResponseEntity<ApiResponse<List<RecommendationRevealResponse>>> getHistory(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(recommendationService.getHistory(userId)));
    }
}
