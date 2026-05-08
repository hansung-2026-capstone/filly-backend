package net.coboogie.recommendation.dto;

/**
 * 공개된 추천 카드 응답.
 *
 * @param drawId        추천 뽑기 세션 ID
 * @param cardId        추천 카드 ID
 * @param category      상위 카테고리
 * @param subCategory   하위 카테고리
 * @param contentType   콘텐츠 타입
 * @param title         추천 제목
 * @param description   추천 설명
 * @param searchKeyword 검색 키워드
 * @param reason        추천 이유
 */
public record RecommendationRevealResponse(
        Long drawId,
        Long cardId,
        String category,
        String subCategory,
        String contentType,
        String title,
        String description,
        String searchKeyword,
        String reason
) {
}
