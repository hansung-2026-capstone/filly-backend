package net.coboogie.persona.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.coboogie.diary.repository.AiEmotionAnalysisRepository;
import net.coboogie.diary.repository.DiaryEntryRepository;
import net.coboogie.persona.dto.PersonaResponse;
import net.coboogie.persona.repository.PersonaSnapshotRepository;
import net.coboogie.user.repository.UserRepository;
import net.coboogie.vo.AiEmotionAnalysisVO;
import net.coboogie.vo.PersonaSnapshotVO;
import net.coboogie.vo.UserVO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * 페르소나 생성 및 이력 조회 서비스.
 * <p>
 * 최근 30일 감정 분석 데이터를 바탕으로 Gemini가 페르소나 제목과 내용을 생성한다.
 * 생성 조건: 최근 30일 일기 5개 이상 + 마지막 생성으로부터 7일 경과.
 */
@Slf4j
@Service
public class PersonaService {

    private static final int REQUIRED_DIARY_COUNT = 5;
    private static final int ANALYSIS_DAYS = 30;
    private static final int GENERATION_INTERVAL_DAYS = 7;

    private final PersonaSnapshotRepository personaSnapshotRepository;
    private final DiaryEntryRepository diaryEntryRepository;
    private final AiEmotionAnalysisRepository aiEmotionAnalysisRepository;
    private final UserRepository userRepository;
    private final ChatClient personaChatClient;
    private final ObjectMapper objectMapper;

    public PersonaService(
            PersonaSnapshotRepository personaSnapshotRepository,
            DiaryEntryRepository diaryEntryRepository,
            AiEmotionAnalysisRepository aiEmotionAnalysisRepository,
            UserRepository userRepository,
            @Qualifier("personaChatClient") ChatClient personaChatClient,
            @Qualifier("aiObjectMapper") ObjectMapper objectMapper) {
        this.personaSnapshotRepository = personaSnapshotRepository;
        this.diaryEntryRepository = diaryEntryRepository;
        this.aiEmotionAnalysisRepository = aiEmotionAnalysisRepository;
        this.userRepository = userRepository;
        this.personaChatClient = personaChatClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 프로필 화면 진입 시 호출되는 메서드.
     * <p>
     * 생성 조건을 충족하면 자동으로 새 페르소나를 생성한 뒤 전체 이력을 반환한다.
     * 조건 미충족 시에는 기존 이력만 반환한다.
     *
     * @param userId 인증 사용자 ID
     * @return 페르소나 이력 목록 (최신순)
     */
    @Transactional
    public List<PersonaResponse> getPersonasWithAutoGenerate(Long userId) {
        try {
            generate(userId);
        } catch (IllegalStateException e) {
            log.info("페르소나 자동 생성 스킵: userId={}, reason={}", userId, e.getMessage());
        }
        return getHistory(userId);
    }

    /**
     * 페르소나 이력을 최신순으로 반환한다.
     *
     * @param userId 인증 사용자 ID
     * @return 페르소나 이력 목록
     */
    @Transactional(readOnly = true)
    public List<PersonaResponse> getHistory(Long userId) {
        return personaSnapshotRepository.findAllByUser_IdOrderByGeneratedAtDesc(userId)
                .stream()
                .map(PersonaResponse::from)
                .toList();
    }

    /**
     * 페르소나를 생성하고 저장한다.
     * <p>
     * 생성 조건을 충족하지 못하면 {@link IllegalStateException}을 던진다.
     *
     * @param userId 인증 사용자 ID
     * @throws IllegalStateException 일기 부족 또는 7일 미경과 시
     */
    @Transactional
    public void generate(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(ANALYSIS_DAYS);

        int diaryCount = diaryEntryRepository.countByUser_IdAndWrittenAtBetween(userId, startDate, today);
        if (diaryCount < REQUIRED_DIARY_COUNT) {
            throw new IllegalStateException("최근 30일 일기가 5개 미만입니다.");
        }

        Optional<PersonaSnapshotVO> latest = personaSnapshotRepository.findLatestByUserId(userId);
        if (latest.isPresent()) {
            LocalDateTime nextAllowed = latest.get().getGeneratedAt().plusDays(GENERATION_INTERVAL_DAYS);
            if (LocalDateTime.now().isBefore(nextAllowed)) {
                throw new IllegalStateException("페르소나는 7일에 한 번 생성할 수 있습니다.");
            }
        }

        UserVO user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다: " + userId));

        List<AiEmotionAnalysisVO> analyses =
                aiEmotionAnalysisRepository.findByUserIdAndDateRange(userId, startDate, today);

        String userMessage = buildUserMessage(diaryCount, startDate, today, analyses);

        String raw = personaChatClient.prompt()
                .user(userMessage)
                .call()
                .content();

        Map<String, String> parsed = parseResponse(raw);

        PersonaSnapshotVO snapshot = PersonaSnapshotVO.builder()
                .user(user)
                .title(parsed.get("title"))
                .summary(parsed.get("summary"))
                .build();

        personaSnapshotRepository.save(snapshot);
        log.info("페르소나 생성 완료: userId={}, title={}", userId, parsed.get("title"));
    }

    /**
     * 감정 분석 데이터를 집계하여 AI에 전달할 user 메시지를 구성한다.
     */
    private String buildUserMessage(int diaryCount, LocalDate startDate, LocalDate endDate,
                                    List<AiEmotionAnalysisVO> analyses) {
        Map<String, Double> emotionScoreSum = new HashMap<>();
        Map<String, Integer> activityFreq = new HashMap<>();
        Map<String, Integer> placeFreq = new HashMap<>();
        Map<String, Integer> iabFreq = new HashMap<>();
        double happinessSum = 0;
        int happinessCount = 0;

        for (AiEmotionAnalysisVO a : analyses) {
            try {
                if (a.getEmotions() != null) {
                    List<Map<String, Object>> emotions = objectMapper.readValue(
                            a.getEmotions(), objectMapper.getTypeFactory()
                                    .constructCollectionType(List.class, Map.class));
                    for (Map<String, Object> e : emotions) {
                        String name = (String) e.get("name");
                        double score = ((Number) e.get("score")).doubleValue();
                        emotionScoreSum.merge(name, score, Double::sum);
                    }
                }
                if (a.getActivities() != null) {
                    List<String> acts = objectMapper.readValue(a.getActivities(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                    acts.forEach(act -> activityFreq.merge(act, 1, Integer::sum));
                }
                if (a.getPlaces() != null) {
                    List<String> places = objectMapper.readValue(a.getPlaces(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                    places.forEach(p -> placeFreq.merge(p, 1, Integer::sum));
                }
                if (a.getIabCategories() != null) {
                    List<String> iabs = objectMapper.readValue(a.getIabCategories(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                    iabs.forEach(i -> iabFreq.merge(i, 1, Integer::sum));
                }
                if (a.getHappinessIndex() != null) {
                    happinessSum += a.getHappinessIndex();
                    happinessCount++;
                }
            } catch (Exception e) {
                log.warn("페르소나 집계 중 파싱 실패: analysisId={}", a.getId());
            }
        }

        int avgHappiness = happinessCount > 0 ? (int) (happinessSum / happinessCount) : 0;
        String topEmotions = formatTopEntries(emotionScoreSum, 5);
        String topActivities = formatTopKeys(activityFreq, 5);
        String topPlaces = formatTopKeys(placeFreq, 5);
        String topIab = formatTopKeys(iabFreq, 8);

        return """
                기간: %s ~ %s
                일기 수: %d개
                주요 감정: %s
                행복 지수 평균: %d
                자주 한 활동: %s
                자주 간 장소: %s
                취향 태그: %s
                """.formatted(startDate, endDate, diaryCount,
                topEmotions, avgHappiness, topActivities, topPlaces, topIab);
    }

    private String formatTopEntries(Map<String, Double> map, int limit) {
        return map.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(e -> e.getKey() + "(" + String.format("%.1f", e.getValue()) + ")")
                .reduce((a, b) -> a + ", " + b)
                .orElse("없음");
    }

    private String formatTopKeys(Map<String, Integer> map, int limit) {
        return map.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .reduce((a, b) -> a + ", " + b)
                .orElse("없음");
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseResponse(String raw) {
        raw = raw.replaceAll("(?s)```json\\s*", "").replaceAll("```", "").strip();
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            raw = raw.substring(start, end + 1);
        }
        try {
            return objectMapper.readValue(raw, Map.class);
        } catch (Exception e) {
            log.warn("페르소나 응답 파싱 실패. raw={}", raw, e);
            throw new IllegalStateException("AI 페르소나 응답을 파싱할 수 없습니다.", e);
        }
    }
}
