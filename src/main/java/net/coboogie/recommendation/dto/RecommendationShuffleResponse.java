package net.coboogie.recommendation.dto;

import java.util.List;

/**
 * 다른 추천 보기 응답.
 *
 * @param drawId 추천 뽑기 세션 ID
 * @param round  현재 라운드
 * @param cards  다시 구성된 카드 뒷면 목록
 */
public record RecommendationShuffleResponse(
        Long drawId,
        int round,
        List<RecommendationCardResponse> cards
) {
}
