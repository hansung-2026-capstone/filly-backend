package net.coboogie.diary.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.coboogie.diary.dto.DiaryDraftResponse;
import net.coboogie.diary.repository.AiEmotionAnalysisRepository;
import net.coboogie.diary.repository.DiaryEntryRepository;
import net.coboogie.stat.repository.MonthlyStatRepository;
import net.coboogie.vo.DiaryEntryVO;
import net.coboogie.vo.UserVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiaryAnalysisAsyncServiceTest {

    @Mock
    private AiDraftGeneratorService aiDraftGeneratorService;

    @Mock
    private DiaryEntryRepository diaryEntryRepository;

    @Mock
    private AiEmotionAnalysisRepository aiEmotionAnalysisRepository;

    @Mock
    private MonthlyStatRepository monthlyStatRepository;

    @Mock(name = "aiObjectMapper")
    private ObjectMapper objectMapper;

    @InjectMocks
    private DiaryAnalysisAsyncService sut;

    @Test
    @DisplayName("비동기 감정 분석 저장 후 해당 월 통계 캐시를 무효화한다")
    void givenAnalyzableDiary_whenAnalyzeAndSaveAsync_thenInvalidateMonthlyStatAfterSave() throws Exception {
        Long userId = 1L;
        Long diaryId = 10L;
        LocalDate writtenAt = LocalDate.of(2026, 4, 10);
        UserVO user = UserVO.builder().id(userId).build();
        DiaryEntryVO diary = DiaryEntryVO.builder().id(diaryId).user(user).writtenAt(writtenAt).build();

        DiaryDraftResponse.AiAnalysis analysis = new DiaryDraftResponse.AiAnalysis(
                List.of(new net.coboogie.diary.dto.AiDraftResult.EmotionScore("기쁨", 0.8f)),
                80,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new net.coboogie.diary.dto.AiDraftResult.Patterns("오후", 1, "혼자", false, null, "맑음", "좋음", "언급없음"),
                "좋은 하루",
                "실시간"
        );

        when(diaryEntryRepository.findById(diaryId)).thenReturn(Optional.of(diary));
        when(aiDraftGeneratorService.analyzeDiaryMultimodal(
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(analysis);
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        sut.analyzeAndSaveAsync(diaryId, "오늘은 좋았다", writtenAt, userId, "female", "20대", "warm");

        verify(aiEmotionAnalysisRepository).save(any());
        verify(monthlyStatRepository).deleteByUserIdAndRecordMonth(userId, "2026-04");
    }
}
