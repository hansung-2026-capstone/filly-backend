package net.coboogie.share.dto;

import java.util.List;

/**
 * ID 카드 공유 콘텐츠 응답 DTO.
 *
 * @param avatarUrl 사용자 아바타 이미지 URL
 * @param nickname  사용자 닉네임
 * @param keywords  일기 분석에서 추출된 IAB 취향 키워드 목록
 */
public record IdCardResponse(
        String avatarUrl,
        String nickname,
        List<String> keywords
) {}
