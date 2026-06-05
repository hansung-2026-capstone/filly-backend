package net.coboogie.diary.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coboogie.diary.dto.DiaryDraftResponse;
import net.coboogie.diary.repository.AiEmotionAnalysisRepository;
import net.coboogie.diary.repository.DiaryEntryRepository;
import net.coboogie.stat.repository.MonthlyStatRepository;
import net.coboogie.vo.AiEmotionAnalysisVO;
import net.coboogie.vo.DiaryEntryVO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 저장 응답을 지연시키지 않도록 일기 AI 분석을 비동기로 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryAnalysisAsyncService {

    private final AiDraftGeneratorService aiDraftGeneratorService;
    private final DiaryEntryRepository diaryEntryRepository;
    private final AiEmotionAnalysisRepository aiEmotionAnalysisRepository;
    private final MonthlyStatRepository monthlyStatRepository;
    @Qualifier("aiObjectMapper")
    private final ObjectMapper objectMapper;

    /**
     * 비동기로 일기 감정 분석을 생성하고 저장한다.
     */
    @Async
    @Transactional
    public void analyzeAndSaveAsync(Long diaryId, String rawContent, LocalDate writtenAt, Long userId,
                                    String gender, String ageGroup, String aiDraftTone) {
        long startedAt = System.currentTimeMillis();
        try {
            DiaryEntryVO diary = diaryEntryRepository.findById(diaryId)
                    .orElseThrow(() -> new IllegalArgumentException("일기를 찾을 수 없습니다: " + diaryId));
            DiaryDraftResponse.AiAnalysis analysis = aiDraftGeneratorService.analyzeDiaryMultimodal(
                    rawContent,
                    null,
                    null,
                    writtenAt,
                    gender,
                    ageGroup,
                    aiDraftTone
            );
            saveEmotionAnalysis(diary, analysis);
            invalidateMonthlyStat(userId, writtenAt);
            log.info("일기 AI 분석 비동기 저장 완료: diaryId={} elapsedMs={}",
                    diaryId, System.currentTimeMillis() - startedAt);
        } catch (Exception e) {
            log.error("일기 AI 분석 비동기 저장 실패: diaryId={}", diaryId, e);
        }
    }

    private void saveEmotionAnalysis(DiaryEntryVO diary, DiaryDraftResponse.AiAnalysis analysis) {
        try {
            AiEmotionAnalysisVO vo = AiEmotionAnalysisVO.builder()
                    .diary(diary)
                    .emotions(objectMapper.writeValueAsString(analysis.emotions()))
                    .happinessIndex(analysis.happinessIndex())
                    .activities(objectMapper.writeValueAsString(analysis.activities()))
                    .places(objectMapper.writeValueAsString(analysis.places()))
                    .people(objectMapper.writeValueAsString(analysis.people()))
                    .iabCategories(objectMapper.writeValueAsString(analysis.iabCategories()))
                    .patterns(objectMapper.writeValueAsString(analysis.patterns()))
                    .moodSummary(analysis.moodSummary())
                    .tone(analysis.tone())
                    .build();
            aiEmotionAnalysisRepository.save(vo);
        } catch (JsonProcessingException e) {
            log.warn("감정 분석 직렬화 실패: diaryId={}", diary.getId(), e);
        }
    }

    private void invalidateMonthlyStat(Long userId, LocalDate writtenAt) {
        if (userId == null || writtenAt == null) {
            return;
        }
        monthlyStatRepository.deleteByUserIdAndRecordMonth(userId, YearMonth.from(writtenAt).toString());
    }
}
