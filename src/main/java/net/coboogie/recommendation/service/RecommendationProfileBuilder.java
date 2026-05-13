package net.coboogie.recommendation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coboogie.diary.repository.AiEmotionAnalysisRepository;
import net.coboogie.persona.repository.PersonaSnapshotRepository;
import net.coboogie.vo.AiEmotionAnalysisVO;
import net.coboogie.vo.PersonaSnapshotVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 저장된 일기 분석 데이터를 추천 프로필로 집계하는 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationProfileBuilder {

    private static final int RECENT_DAYS = 90;
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> OBJECT_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> OBJECT_MAP_TYPE = new TypeReference<>() {};

    private final AiEmotionAnalysisRepository aiEmotionAnalysisRepository;
    private final PersonaSnapshotRepository personaSnapshotRepository;
    private final ObjectMapper objectMapper;

    /**
     * 사용자 추천 프로필을 최근 90일 기준으로 만들고, 데이터가 없으면 전체 분석 데이터를 사용한다.
     *
     * @param userId 사용자 ID
     * @return 추천 프로필
     */
    @Transactional(readOnly = true)
    public RecommendationProfile build(Long userId) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(RECENT_DAYS);
        List<AiEmotionAnalysisVO> analyses =
                aiEmotionAnalysisRepository.findByUserIdAndDateRange(userId, startDate, endDate);
        String sourcePeriod = startDate + "~" + endDate;
        if (analyses.isEmpty()) {
            analyses = aiEmotionAnalysisRepository.findByDiary_User_Id(userId);
            sourcePeriod = "ALL";
        }

        ProfileAccumulator acc = new ProfileAccumulator();
        for (AiEmotionAnalysisVO analysis : analyses) {
            collectIabCategories(analysis, acc);
            collectStringList(analysis.getActivities(), acc.activityFreq(), analysis.getId(), "activities");
            collectStringList(analysis.getPlaces(), acc.placeFreq(), analysis.getId(), "places");
            collectEmotions(analysis, acc);
            collectPatterns(analysis, acc);
            if (analysis.getHappinessIndex() != null) {
                acc.addHappiness(analysis.getHappinessIndex());
            }
        }

        PersonaSnapshotVO persona = personaSnapshotRepository.findLatestByUserId(userId).orElse(null);
        int avgHappiness = acc.happinessCount() == 0 ? 0 : acc.happinessSum() / acc.happinessCount();

        return new RecommendationProfile(
                sourcePeriod,
                sortByValue(acc.mainCategoryFreq()),
                sortByValue(acc.subCategoryFreq()),
                sortDoubleByValue(acc.emotionScoreSum()),
                sortByValue(acc.activityFreq()),
                sortByValue(acc.placeFreq()),
                sortNested(acc.patternFreq()),
                avgHappiness,
                persona == null ? null : persona.getTitle(),
                persona == null ? null : persona.getSummary()
        );
    }

    private void collectIabCategories(AiEmotionAnalysisVO analysis, ProfileAccumulator acc) {
        if (analysis.getIabCategories() == null) {
            return;
        }
        try {
            List<String> categories = objectMapper.readValue(analysis.getIabCategories(), STRING_LIST_TYPE);
            for (String category : categories) {
                if (category == null || category.isBlank()) {
                    continue;
                }
                String main = splitMainCategory(category);
                acc.mainCategoryFreq().merge(main, 1, Integer::sum);
                acc.subCategoryFreq().merge(category, 1, Integer::sum);
            }
        } catch (Exception e) {
            log.warn("추천 프로필 IAB 파싱 실패: analysisId={}", analysis.getId());
        }
    }

    private void collectStringList(String json, Map<String, Integer> target, Long analysisId, String field) {
        if (json == null) {
            return;
        }
        try {
            List<String> values = objectMapper.readValue(json, STRING_LIST_TYPE);
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    target.merge(value, 1, Integer::sum);
                }
            }
        } catch (Exception e) {
            log.warn("추천 프로필 {} 파싱 실패: analysisId={}", field, analysisId);
        }
    }

    private void collectEmotions(AiEmotionAnalysisVO analysis, ProfileAccumulator acc) {
        if (analysis.getEmotions() == null) {
            return;
        }
        try {
            List<Map<String, Object>> emotions = objectMapper.readValue(analysis.getEmotions(), OBJECT_LIST_TYPE);
            for (Map<String, Object> emotion : emotions) {
                Object nameValue = emotion.get("name");
                Object scoreValue = emotion.get("score");
                if (nameValue instanceof String name && scoreValue instanceof Number score) {
                    acc.emotionScoreSum().merge(name, score.doubleValue(), Double::sum);
                }
            }
        } catch (Exception e) {
            log.warn("추천 프로필 감정 파싱 실패: analysisId={}", analysis.getId());
        }
    }

    private void collectPatterns(AiEmotionAnalysisVO analysis, ProfileAccumulator acc) {
        if (analysis.getPatterns() == null) {
            return;
        }
        try {
            Map<String, Object> patterns = objectMapper.readValue(analysis.getPatterns(), OBJECT_MAP_TYPE);
            patterns.forEach((key, value) -> mergePatternValue(acc.patternFreq(), key, value));
        } catch (Exception e) {
            log.warn("추천 프로필 패턴 파싱 실패: analysisId={}", analysis.getId());
        }
    }

    private void mergePatternValue(Map<String, Map<String, Integer>> patternFreq, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Collection<?> values) {
            values.forEach(item -> mergePatternValue(patternFreq, key, item));
            return;
        }
        String normalized = String.valueOf(value).strip();
        if (normalized.isEmpty()) {
            return;
        }
        patternFreq.computeIfAbsent(key, k -> new HashMap<>())
                .merge(normalized, 1, Integer::sum);
    }

    private String splitMainCategory(String category) {
        int delimiter = category.indexOf('>');
        return delimiter > 0 ? category.substring(0, delimiter).trim() : category.trim();
    }

    private Map<String, Integer> sortByValue(Map<String, Integer> map) {
        if (map.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        map.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

    private Map<String, Double> sortDoubleByValue(Map<String, Double> map) {
        if (map.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Double> result = new LinkedHashMap<>();
        map.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

    private Map<String, Map<String, Integer>> sortNested(Map<String, Map<String, Integer>> map) {
        Map<String, Map<String, Integer>> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(key, sortByValue(value)));
        return result;
    }

    private static final class ProfileAccumulator {
        private final Map<String, Integer> mainCategoryFreq = new HashMap<>();
        private final Map<String, Integer> subCategoryFreq = new HashMap<>();
        private final Map<String, Double> emotionScoreSum = new HashMap<>();
        private final Map<String, Integer> activityFreq = new HashMap<>();
        private final Map<String, Integer> placeFreq = new HashMap<>();
        private final Map<String, Map<String, Integer>> patternFreq = new HashMap<>();
        private int happinessSum;
        private int happinessCount;

        private Map<String, Integer> mainCategoryFreq() {
            return mainCategoryFreq;
        }

        private Map<String, Integer> subCategoryFreq() {
            return subCategoryFreq;
        }

        private Map<String, Double> emotionScoreSum() {
            return emotionScoreSum;
        }

        private Map<String, Integer> activityFreq() {
            return activityFreq;
        }

        private Map<String, Integer> placeFreq() {
            return placeFreq;
        }

        private Map<String, Map<String, Integer>> patternFreq() {
            return patternFreq;
        }

        private int happinessSum() {
            return happinessSum;
        }

        private int happinessCount() {
            return happinessCount;
        }

        private void addHappiness(int happinessIndex) {
            happinessSum += happinessIndex;
            happinessCount++;
        }
    }
}
