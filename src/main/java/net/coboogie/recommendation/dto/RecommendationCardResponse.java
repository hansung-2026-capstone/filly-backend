package net.coboogie.recommendation.dto;

/**
 * 추천 카드 뒷면 응답.
 *
 * @param cardId   추천 카드 ID
 * @param position 화면 표시 위치
 * @param revealed 공개 여부
 */
public record RecommendationCardResponse(Long cardId, Integer position, boolean revealed) {
}
