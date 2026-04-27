package net.coboogie.share.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coboogie.diary.repository.AiEmotionAnalysisRepository;
import net.coboogie.diary.repository.DiaryEntryRepository;
import net.coboogie.persona.repository.PersonaSnapshotRepository;
import net.coboogie.share.dto.IdCardResponse;
import net.coboogie.share.dto.ReceiptResponse;
import net.coboogie.user.repository.UserRepository;
import net.coboogie.vo.AiEmotionAnalysisVO;
import net.coboogie.vo.DiaryEntryVO;
import net.coboogie.vo.UserVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 공유 콘텐츠 도메인 비즈니스 로직 서비스.
 * <p>
 * ID 카드(아바타·닉네임·키워드)와 영수증(월별 통계) 데이터를 조합하여 반환한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShareService {

    private static final int MAX_KEYWORDS = 10;
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> EMOTION_LIST_TYPE = new TypeReference<>() {};

    private final UserRepository userRepository;
    private final DiaryEntryRepository diaryEntryRepository;
    private final AiEmotionAnalysisRepository aiEmotionAnalysisRepository;
    private final PersonaSnapshotRepository personaSnapshotRepository;
    private final ObjectMapper objectMapper;

    /**
     * 사용자의 ID 카드 공유 데이터를 반환한다.
     * <p>
     * 아바타 URL, 닉네임, 전체 일기 감정 분석에서 추출한 IAB 키워드를 포함한다.
     *
     * @param userId JWT 인증 사용자 ID
     * @return ID 카드 응답 DTO
     * @throws NoSuchElementException 사용자가 존재하지 않는 경우
     */
    @Transactional(readOnly = true)
    public IdCardResponse getIdCard(Long userId) {
        UserVO user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다: " + userId));

        List<AiEmotionAnalysisVO> analyses = aiEmotionAnalysisRepository.findByDiary_User_Id(userId);
        List<String> keywords = extractKeywords(analyses);

        return new IdCardResponse(user.getCurrentAvatarUrl(), user.getNickname(), keywords);
    }

    /**
     * 특정 연월의 영수증 공유 데이터를 반환한다.
     * <p>
     * 일기 개수, 총 글자 수, 감정 분포, 최근 페르소나 제목을 포함한다.
     * 주문번호는 {@code FL-YYYYMMDD-NNN} 형식으로 생성된다.
     *
     * @param userId JWT 인증 사용자 ID
     * @param year   조회 연도
     * @param month  조회 월 (1~12)
     * @return 영수증 응답 DTO
     * @throws NoSuchElementException 사용자가 존재하지 않는 경우
     */
    @Transactional(readOnly = true)
    public ReceiptResponse getReceipt(Long userId, int year, int month) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다: " + userId));

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        List<DiaryEntryVO> diaries = diaryEntryRepository
                .findByUser_IdAndWrittenAtBetweenOrderByWrittenAtAsc(userId, startDate, endDate);

        int diaryCount = diaries.size();
        int totalChars = diaries.stream()
                .mapToInt(d -> d.getRawContent() == null ? 0 : d.getRawContent().length())
                .sum();

        List<Long> diaryIds = diaries.stream().map(DiaryEntryVO::getId).toList();
        List<AiEmotionAnalysisVO> analyses = diaryIds.isEmpty()
                ? Collections.emptyList()
                : aiEmotionAnalysisRepository.findByDiary_IdIn(diaryIds);
        Map<String, Integer> emotionDistribution = calcEmotionDistribution(analyses);

        String personaTitle = personaSnapshotRepository
                .findLatestByUserId(userId)
                .map(p -> p.getTitle())
                .orElse(null);

        String orderNumber = buildOrderNumber(diaryCount);

        return new ReceiptResponse(orderNumber, diaryCount, totalChars, emotionDistribution, personaTitle);
    }

    /**
     * 감정 분석 목록에서 IAB 카테고리 키워드를 추출하고 빈도 순으로 정렬하여 반환한다.
     * 파싱 실패한 항목은 건너뛴다.
     */
    private List<String> extractKeywords(List<AiEmotionAnalysisVO> analyses) {
        Map<String, Integer> freq = new HashMap<>();
        for (AiEmotionAnalysisVO analysis : analyses) {
            if (analysis.getIabCategories() == null) {
                continue;
            }
            try {
                List<String> categories = objectMapper.readValue(analysis.getIabCategories(), STRING_LIST_TYPE);
                for (String category : categories) {
                    freq.merge(category, 1, Integer::sum);
                }
            } catch (Exception e) {
                log.warn("IAB 카테고리 파싱 실패: analysisId={}", analysis.getId());
            }
        }
        return freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(MAX_KEYWORDS)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * 감정 분석 목록에서 감정별 비율(%)을 계산하여 반환한다.
     * 감정 분석이 없으면 빈 맵을 반환한다.
     */
    private Map<String, Integer> calcEmotionDistribution(List<AiEmotionAnalysisVO> analyses) {
        if (analyses.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Double> scoreSum = new HashMap<>();
        for (AiEmotionAnalysisVO analysis : analyses) {
            if (analysis.getEmotions() == null) {
                continue;
            }
            try {
                List<Map<String, Object>> emotions = objectMapper.readValue(analysis.getEmotions(), EMOTION_LIST_TYPE);
                for (Map<String, Object> emotion : emotions) {
                    String name = (String) emotion.get("name");
                    double score = ((Number) emotion.get("score")).doubleValue();
                    scoreSum.merge(name, score, Double::sum);
                }
            } catch (Exception e) {
                log.warn("감정 분석 파싱 실패: analysisId={}", analysis.getId());
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
     * {@code FL-YYYYMMDD-NNN} 형식의 주문번호를 생성한다.
     * NNN은 해당 월 일기 개수를 3자리로 패딩한 값이다.
     */
    private String buildOrderNumber(int diaryCount) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return String.format("FL-%s-%03d", date, diaryCount);
    }
}
