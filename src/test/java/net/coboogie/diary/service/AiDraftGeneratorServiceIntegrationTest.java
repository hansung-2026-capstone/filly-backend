package net.coboogie.diary.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.vertexai.VertexAI;
import net.coboogie.diary.dto.AiDraftResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AiDraftGeneratorService 실제 Vertex AI Gemini API 호출 통합 테스트.
 *
 * 실행 전 필수:
 *   gcloud auth application-default login
 *
 * CI에서는 실행되지 않도록 @Disabled 처리. 로컬에서 수동 실행.
 */
//@Disabled("로컬 ADC 인증 필요 - 수동 실행 테스트")  // 실행 시 이 줄 삭제
class AiDraftGeneratorServiceIntegrationTest {

    private static final String PROJECT_ID = "filly-492515";
    private static final String LOCATION   = "us-central1";
    private static final String MODEL      = "gemini-2.0-flash-001";

    private VertexAI vertexAI;
    private AiDraftGeneratorService sut;

    @BeforeEach
    void setUp() throws Exception {
        vertexAI = new VertexAI(PROJECT_ID, LOCATION);

        VertexAiGeminiChatModel chatModel = VertexAiGeminiChatModel.builder()
                .vertexAI(vertexAI)
                .defaultOptions(VertexAiGeminiChatOptions.builder().model(MODEL).build())
                .build();

        ChatClient chatClient = ChatClient.builder(chatModel).build();

        sut = new AiDraftGeneratorService(chatClient, new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (vertexAI != null) {
            vertexAI.close();
        }
    }

    @Test
    @DisplayName("텍스트 입력 시 Gemini가 일기 초안과 감정 분석을 반환한다")
    void givenTextInput_whenGenerate_thenReturnAiDraftResult() {
        // when
        AiDraftResult result = sut.generate(
                "오늘 친구들과 한강에서 치킨을 먹었다. 날씨도 좋고 정말 즐거웠다.",
                List.of(),
                null,
                LocalDate.of(2026, 4, 11)
        );

        // then
        System.out.println("=== Gemini 응답 ===");
        System.out.println("일기: " + result.generatedText());
        System.out.println("감정: " + result.emotions());
        System.out.println("행복지수: " + result.happinessIndex());
        System.out.println("활동: " + result.activities());
        System.out.println("장소: " + result.places());
        System.out.println("사람: " + result.people());
        System.out.println("IAB: " + result.iabCategories());
        System.out.println("패턴: " + result.patterns());
        System.out.println("요약: " + result.moodSummary());

        assertThat(result.generatedText()).isNotBlank();
        assertThat(result.emotions()).isNotEmpty();
        assertThat(result.happinessIndex()).isBetween(0, 100);
        assertThat(result.moodSummary()).isNotBlank();
    }

    @Test
    @DisplayName("이미지 캡션과 텍스트를 함께 입력하면 결합된 일기 초안을 반환한다")
    void givenTextAndCaptions_whenGenerate_thenReturnCombinedDraft() {
        // when
        AiDraftResult result = sut.generate(
                "오늘 카페에 갔다",
                List.of("아늑한 카페 내부", "커피 한 잔과 책"),
                null,
                LocalDate.of(2026, 4, 11)
        );

        // then
        System.out.println("=== Gemini 응답 (텍스트 + 캡션) ===");
        System.out.println("일기: " + result.generatedText());
        System.out.println("감정: " + result.emotions());
        System.out.println("요약: " + result.moodSummary());

        assertThat(result.generatedText()).isNotBlank();
        assertThat(result.emotions()).isNotEmpty();
    }
}
