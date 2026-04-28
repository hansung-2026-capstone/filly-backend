package net.coboogie.stat.dto;

import java.util.List;
import java.util.Map;

/**
 * 월별 통계 응답 DTO.
 *
 * @param recordMonth        연월 ({@code "YYYY-MM"})
 * @param diaryCount         해당 월 일기 개수
 * @param totalChars         해당 월 총 글자 수
 * @param emotionDistribution 감정 분포 (감정명 → 비율 %)
 * @param keywordCloud       IAB 카테고리 빈도 (카테고리명 → 등장 횟수)
 * @param topPeople          자주 등장한 인물 목록 (최대 10명)
 * @param dailyPattern       레이어4 패턴 누적 (패턴 항목 → 값 → 횟수)
 */
public record MonthlyStatResponse(
        String recordMonth,
        int diaryCount,
        int totalChars,
        Map<String, Integer> emotionDistribution,
        Map<String, Integer> keywordCloud,
        List<String> topPeople,
        Map<String, Map<String, Integer>> dailyPattern
) {}
