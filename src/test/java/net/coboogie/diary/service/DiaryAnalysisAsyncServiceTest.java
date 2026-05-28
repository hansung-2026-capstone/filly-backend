package net.coboogie.diary.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.coboogie.diary.dto.AiDraftResult;
import net.coboogie.diary.repository.AiEmotionAnalysisRepository;
import net.coboogie.diary.repository.DiaryEntryRepository;
import net.coboogie.vo.AiEmotionAnalysisVO;
import net.coboogie.vo.DiaryEntryVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DiaryAnalysisAsyncServiceTest {

    @Mock private AiDraftGeneratorService aiDraftGeneratorService;
    @Mock private DiaryEntryRepository diaryEntryRepository;
    @Mock private AiEmotionAnalysisRepository aiEmotionAnalysisRepository;
    @Mock private ObjectMapper objectMapper;

    private DiaryAnalysisAsyncService sut;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager transactionManager = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };

        sut = new DiaryAnalysisAsyncService(
                aiDraftGeneratorService,
                diaryEntryRepository,
                aiEmotionAnalysisRepository,
                objectMapper,
                transactionManager
        );
    }

    @Test
    @DisplayName("AI 분석 결과를 감정 분석 엔티티로 저장한다")
    @SuppressWarnings("unchecked")
    void givenAiResult_whenAnalyzeAndSaveAsync_thenSaveEmotionAnalysis() throws JsonProcessingException {
        // given
        Long diaryId = 1L;
        DiaryEntryVO diary = DiaryEntryVO.builder()
                .id(diaryId)
                .writtenAt(LocalDate.now())
                .build();
        AiDraftResult result = new AiDraftResult(
                "generated",
                List.of(new AiDraftResult.EmotionScore("happy", 0.8F)),
                80,
                List.of("reading"),
                List.of("home"),
                List.of(new AiDraftResult.PersonTag("민수", "friend", "positive")),
                List.of("IAB1"),
                new AiDraftResult.Patterns("evening", 4, "alone", false, null, "sunny", "good", "normal"),
                "차분한 하루",
                "warm"
        );

        given(diaryEntryRepository.findById(diaryId)).willReturn(Optional.of(diary));
        given(aiDraftGeneratorService.generate(
                eq("raw"),
                any(List.class),
                eq(null),
                any(LocalDate.class),
                eq("none"),
                eq("none"),
                eq("none")
        )).willReturn(result);
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        // when
        sut.analyzeAndSaveAsync(diaryId, "raw", LocalDate.now(), "none", "none", "none", List.of());

        // then
        verify(aiEmotionAnalysisRepository).save(any(AiEmotionAnalysisVO.class));
    }
}
