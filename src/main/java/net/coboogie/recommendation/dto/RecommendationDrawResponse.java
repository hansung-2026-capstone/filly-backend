package net.coboogie.recommendation.dto;

import java.util.List;

/**
 * 추천 뽑기 시작 응답.
 *
 * @param drawId 추천 뽑기 세션 ID
 * @param round  현재 라운드
 * @param cards  카드 뒷면 목록
 */
public record RecommendationDrawResponse(
        Long drawId,
        int round,
        List<RecommendationCardResponse> cards
) {
}
