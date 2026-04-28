package net.coboogie.stat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coboogie.diary.repository.AiEmotionAnalysisRepository;
import net.coboogie.diary.repository.DiaryEntryRepository;
import net.coboogie.stat.dto.MonthlyStatResponse;
import net.coboogie.stat.repository.MonthlyStatRepository;
import net.coboogie.user.repository.UserRepository;
import net.coboogie.vo.AiEmotionAnalysisVO;
import net.coboogie.vo.DiaryEntryVO;
import net.coboogie.vo.MonthlyStatVO;
import net.coboogie.vo.UserVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * 월별 통계 도메인 비즈니스 로직 서비스.
 * <p>
 * {@code monthly_stats}에 캐시된 데이터가 없으면 {@code ai_diary_analysis}를 즉시 집계하여 저장 후 반환한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatService {

    private static final int TOP_PEOPLE_LIMIT = 10;

    private static final TypeReference<List<Map<String, Object>>> OBJ_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Integer>> INT_MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> STR_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Map<String, Integer>>> PATTERN_MAP_TYPE = new TypeReference<>() {};

    private final MonthlyStatRepository monthlyStatRepository;
    private final DiaryEntryRepository diaryEntryRepository;
    private final AiEmotionAnalysisRepository aiEmotionAnalysisRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /**
     * 월별 통계를 조회한다. 캐시된 데이터가 없으면 즉시 집계 후 저장하여 반환한다.
     *
     * @param userId 인증 사용자 ID
     * @param year   조회 연도
     * @param month  조회 월 (1~12)
     * @return 월별 통계 응답 DTO
     * @throws NoSuchElementException 사용자가 존재하지 않는 경우
     */
    @Transactional
    public MonthlyStatResponse getOrCalculate(Long userId, int year, int month) {
        String recordMonth = String.format("%04d-%02d", year, month);
        return monthlyStatRepository.findByUserIdAndRecordMonth(userId, recordMonth)
                .map(this::toResponse)
                .orElseGet(() -> calculateAndSave(userId, year, month, recordMonth));
    }

    /**
     * 해당 월의 일기와 감정 분석 데이터를 집계하고 {@code monthly_stats}에 저장한 뒤 반환한다.
     */
    private MonthlyStatResponse calculateAndSave(Long userId, int year, int month, String recordMonth) {
        YearMonth yearMonth = YearMonth.of(year, month);

        List<DiaryEntryVO> diaries = diaryEntryRepository
                .findByUser_IdAndWrittenAtBetweenOrderByWrittenAtAsc(
                        userId, yearMonth.atDay(1), yearMonth.atEndOfMonth());

        List<Long> diaryIds = diaries.stream().map(DiaryEntryVO::getId).toList();
        List<AiEmotionAnalysisVO> analyses = diaryIds.isEmpty()
                ? Collections.emptyList()
                : aiEmotionAnalysisRepository.findByDiary_IdIn(diaryIds);

        int diaryCount = diaries.size();
        int totalChars = diaries.stream()
                .mapToInt(d -> d.getRawContent() == null ? 0 : d.getRawContent().length())
                .sum();

        Map<String, Integer> emotionDistribution = calcEmotionDistribution(analyses);
        Map<String, Integer> keywordCloud = calcKeywordCloud(analyses);
        List<String> topPeople = calcTopPeople(analyses);
        Map<String, Map<String, Integer>> dailyPattern = calcDailyPattern(analyses);

        UserVO user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다: " + userId));

        MonthlyStatVO stat = MonthlyStatVO.builder()
                .user(user)
                .recordMonth(recordMonth)
                .diaryCount(diaryCount)
                .totalChars(totalChars)
                .emotionDistribution(toJson(emotionDistribution))
                .keywordCloud(toJson(keywordCloud))
                .topPeople(toJson(topPeople))
                .dailyPattern(toJson(dailyPattern))
                .build();

        monthlyStatRepository.save(stat);

        return new MonthlyStatResponse(recordMonth, diaryCount, totalChars,
                emotionDistribution, keywordCloud, topPeople, dailyPattern);
    }

    /**
     * {@code MonthlyStatVO}의 JSON 필드를 역직렬화하여 응답 DTO로 변환한다.
     */
    private MonthlyStatResponse toResponse(MonthlyStatVO stat) {
        return new MonthlyStatResponse(
                stat.getRecordMonth(),
                stat.getDiaryCount() != null ? stat.getDiaryCount() : 0,
                stat.getTotalChars() != null ? stat.getTotalChars() : 0,
                parseJson(stat.getEmotionDistribution(), INT_MAP_TYPE),
                parseJson(stat.getKeywordCloud(), INT_MAP_TYPE),
                parseJson(stat.getTopPeople(), STR_LIST_TYPE),
                parseJson(stat.getDailyPattern(), PATTERN_MAP_TYPE)
        );
    }

    /**
     * 감정 분석 목록에서 감정별 비율(%)을 계산한다.
     * {@code emotions} 필드의 score 합산 후 전체 대비 백분율로 변환한다.
     */
    private Map<String, Integer> calcEmotionDistribution(List<AiEmotionAnalysisVO> analyses) {
        Map<String, Double> scoreSum = new HashMap<>();
        for (AiEmotionAnalysisVO analysis : analyses) {
            if (analysis.getEmotions() == null) {
                continue;
            }
            try {
                List<Map<String, Object>> emotions = objectMapper.readValue(analysis.getEmotions(), OBJ_LIST_TYPE);
                for (Map<String, Object> emotion : emotions) {
                    String name = (String) emotion.get("name");
                    double score = ((Number) emotion.get("score")).doubleValue();
                    scoreSum.merge(name, score, Double::sum);
                }
            } catch (Exception e) {
                log.warn("감정 파싱 실패: analysisId={}", analysis.getId());
            }
        }
        double total = scoreSum.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total == 0) {
            return Collections.emptyMap();
        }
        Map<String, Integer> distribution = new LinkedHashMap<>();
        scoreSum.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(e -> distribution.put(e.getKey(), (int) Math.round(e.getValue() / total * 100)));
        return distribution;
    }

    /**
     * 감정 분석 목록에서 IAB 카테고리 등장 빈도를 집계한다.
     */
    private Map<String, Integer> calcKeywordCloud(List<AiEmotionAnalysisVO> analyses) {
        Map<String, Integer> freq = new HashMap<>();
        for (AiEmotionAnalysisVO analysis : analyses) {
            if (analysis.getIabCategories() == null) {
                continue;
            }
            try {
                List<String> categories = objectMapper.readValue(analysis.getIabCategories(), STR_LIST_TYPE);
                for (String category : categories) {
                    freq.merge(category, 1, Integer::sum);
                }
            } catch (Exception e) {
                log.warn("IAB 카테고리 파싱 실패: analysisId={}", analysis.getId());
            }
        }
        return freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    /**
     * 감정 분석 목록에서 자주 등장한 인물을 최대 {@value TOP_PEOPLE_LIMIT}명 반환한다.
     * {@code people} 필드의 {@code name} 속성 기준으로 빈도를 집계한다.
     */
    private List<String> calcTopPeople(List<AiEmotionAnalysisVO> analyses) {
        Map<String, Integer> freq = new HashMap<>();
        for (AiEmotionAnalysisVO analysis : analyses) {
            if (analysis.getPeople() == null) {
                continue;
            }
            try {
                List<Map<String, Object>> people = objectMapper.readValue(analysis.getPeople(), OBJ_LIST_TYPE);
                for (Map<String, Object> person : people) {
                    String name = (String) person.get("name");
                    if (name != null) {
                        freq.merge(name, 1, Integer::sum);
                    }
                }
            } catch (Exception e) {
                log.warn("인물 파싱 실패: analysisId={}", analysis.getId());
            }
        }
        return freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(TOP_PEOPLE_LIMIT)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * 레이어4 {@code patterns} 필드를 누적 집계하여 항목별 값 빈도 맵을 반환한다.
     * 예: {@code {"time_of_day": {"저녁": 8, "아침": 3}, "energy_level": {"보통": 7, ...}}}
     */
    private Map<String, Map<String, Integer>> calcDailyPattern(List<AiEmotionAnalysisVO> analyses) {
        Map<String, Map<String, Integer>> result = new LinkedHashMap<>();
        for (AiEmotionAnalysisVO analysis : analyses) {
            if (analysis.getPatterns() == null) {
                continue;
            }
            try {
                Map<String, Object> patterns = objectMapper.readValue(
                        analysis.getPatterns(), new TypeReference<>() {});
                for (Map.Entry<String, Object> entry : patterns.entrySet()) {
                    if (entry.getValue() == null) {
                        continue;
                    }
                    String key = entry.getKey();
                    String value = String.valueOf(entry.getValue());
                    result.computeIfAbsent(key, k -> new HashMap<>())
                            .merge(value, 1, Integer::sum);
                }
            } catch (Exception e) {
                log.warn("패턴 파싱 실패: analysisId={}", analysis.getId());
            }
        }
        result.replaceAll((key, valueMap) -> valueMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new)));
        return result;
    }

    /**
     * 객체를 JSON 문자열로 직렬화한다. 실패 시 {@code null}을 반환한다.
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("JSON 직렬화 실패: {}", obj);
            return null;
        }
    }

    /**
     * JSON 문자열을 지정된 타입으로 역직렬화한다. 실패하거나 {@code null}이면 빈 컬렉션을 반환한다.
     */
    private <T> T parseJson(String json, TypeReference<T> type) {
        if (json == null) {
            return emptyForType(type);
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("JSON 역직렬화 실패: type={}", type.getType());
            return emptyForType(type);
        }
    }

    /**
     * TypeReference에 대응하는 빈 컬렉션을 반환한다.
     */
    @SuppressWarnings("unchecked")
    private <T> T emptyForType(TypeReference<T> type) {
        String typeName = type.getType().getTypeName();
        if (typeName.startsWith("java.util.List")) {
            return (T) Collections.emptyList();
        }
        return (T) Collections.emptyMap();
    }
}
