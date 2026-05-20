package net.coboogie.stat.dto;

import java.util.List;
import java.util.Map;

/**
 * 월별 통계 응답 DTO.
 *
 * @param recordMonth         연월 ({@code "YYYY-MM"})
 * @param diaryCount          해당 월 일기 개수
 * @param totalChars          해당 월 총 글자 수
 * @param emotionDistribution 감정 분포 (감정명 → 비율 %)
 * @param keywordCloud        IAB 카테고리 빈도 (카테고리명 → 등장 횟수)
 * @param topPeople           자주 등장한 인물 목록 (최대 10명)
 * @param dailyPattern        레이어4 패턴 누적 (패턴 항목 → 값 → 횟수)
 * @param habitDiscoveries    사용자에게 보여줄 개인 습관 발견 후보
 */
public record MonthlyStatResponse(
        String recordMonth,
        int diaryCount,
        int totalChars,
        Map<String, Integer> emotionDistribution,
        Map<String, Integer> keywordCloud,
        List<String> topPeople,
        Map<String, Map<String, Integer>> dailyPattern,
        List<HabitDiscovery> habitDiscoveries
) {
    /**
     * 프론트에서 "내가 몰랐던 습관" 카드로 바로 보여줄 수 있는 표시용 DTO.
     *
     * @param category   습관 분류 라벨
     * @param patternKey 원본 dailyPattern 키
     * @param pattern    발견된 습관 후보 문장
     * @param count      해당 월 등장 횟수
     * @param message    사용자 표시용 문장
     */
    public record HabitDiscovery(
            String category,
            String patternKey,
            String pattern,
            int count,
            String message
    ) {}
}
