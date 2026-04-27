package net.coboogie.diary.dto;

/**
 * 별점 업데이트 요청 DTO.
 * <p>
 * PATCH /api/v1/diaries/{id}/star 요청 바디로 사용된다.
 *
 * @param starRating 별점 (1~5)
 */
public record StarRatingRequest(Integer starRating) {}
