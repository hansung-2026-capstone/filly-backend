package net.coboogie.recommendation.service;

import java.util.List;
import java.util.Map;

/**
 * 추천 생성에 사용하는 사용자 취향 스냅샷.
 *
 * @param sourcePeriod      분석 기준 기간
 * @param mainCategoryFreq  IAB 상위 카테고리 빈도
 * @param subCategoryFreq   IAB 하위 카테고리 빈도
 * @param emotionScoreSum   감정 점수 합계
 * @param activityFreq      활동 빈도
 * @param placeFreq         장소 빈도
 * @param patternFreq       일상 패턴 빈도
 * @param avgHappinessIndex 평균 행복 지수
 * @param personaTitle      최신 페르소나 제목
 * @param personaSummary    최신 페르소나 설명
 */
public record RecommendationProfile(
        int recommendationPromptVersion,
        String sourcePeriod,
        Map<String, Integer> mainCategoryFreq,
        Map<String, Integer> subCategoryFreq,
        Map<String, Double> emotionScoreSum,
        Map<String, Integer> activityFreq,
        Map<String, Integer> placeFreq,
        Map<String, Map<String, Integer>> patternFreq,
        int avgHappinessIndex,
        String personaTitle,
        String personaSummary
) {
    public static final int CURRENT_RECOMMENDATION_PROMPT_VERSION = 4;

    /**
     * 추천 프롬프트에 전달할 간결한 요약 문자열을 만든다.
     *
     * @return 사용자 취향 요약
     */
    public String toPromptSummary() {
        return """
                분석 기간: %s
                IAB 상위 카테고리: %s
                IAB 하위 카테고리: %s
                주요 감정: %s
                평균 행복 지수: %d
                주요 활동: %s
                주요 장소: %s
                우선 반영할 개인 습관 후보: %s
                일상 패턴: %s
                페르소나 제목: %s
                페르소나 설명: %s
                """.formatted(
                sourcePeriod,
                topEntries(mainCategoryFreq, 8),
                topEntries(subCategoryFreq, 10),
                topEntries(emotionScoreSum, 6),
                avgHappinessIndex,
                topEntries(activityFreq, 8),
                topEntries(placeFreq, 8),
                summarizePersonalHabitCandidates(patternFreq),
                summarizePatterns(patternFreq),
                valueOrNone(personaTitle),
                valueOrNone(personaSummary)
        );
    }

    private static <T extends Number> String topEntries(Map<String, T> map, int limit) {
        if (map == null || map.isEmpty()) {
            return "없음";
        }
        return map.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue().doubleValue(), a.getValue().doubleValue()))
                .limit(limit)
                .map(e -> e.getKey() + "(" + e.getValue() + ")")
                .reduce((a, b) -> a + ", " + b)
                .orElse("없음");
    }

    private static String summarizePatterns(Map<String, Map<String, Integer>> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return "없음";
        }
        List<String> summaries = patterns.entrySet().stream()
                .map(e -> e.getKey() + "=" + topEntries(e.getValue(), 3))
                .toList();
        return String.join(" / ", summaries);
    }

    private static String summarizePersonalHabitCandidates(Map<String, Map<String, Integer>> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return "없음";
        }
        List<String> priorityKeys = List.of(
                "personalPatternCandidates",
                "personal_pattern_candidates",
                "weekdayPattern",
                "weekday_pattern",
                "mealPattern",
                "meal_pattern",
                "caffeinePattern",
                "caffeine_pattern",
                "wakeTime",
                "wake_time",
                "sleepTime",
                "sleep_time"
        );
        return priorityKeys.stream()
                .filter(patterns::containsKey)
                .map(key -> key + "=" + topEntries(patterns.get(key), 3))
                .reduce((a, b) -> a + " / " + b)
                .orElse("없음");
    }

    private static String valueOrNone(String value) {
        return value == null || value.isBlank() ? "없음" : value;
    }
}
