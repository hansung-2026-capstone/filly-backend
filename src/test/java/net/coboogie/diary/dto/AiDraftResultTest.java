package net.coboogie.diary.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.coboogie.auth.config.JacksonConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiDraftResultTest {

    private final ObjectMapper objectMapper = new JacksonConfig().aiObjectMapper();

    @Test
    @DisplayName("AI 응답 patterns가 snake_case이면 파싱된다")
    void givenSnakeCasePatterns_whenDeserialize_thenParseAiDraftResult() throws Exception {
        AiDraftResult result = objectMapper.readValue(snakeCaseJson(), AiDraftResult.class);

        assertThat(result.patterns().timeOfDay()).isEqualTo("오후");
        assertThat(result.patterns().energyLevel()).isEqualTo(7);
        assertThat(result.patterns().spendingCategory()).isEqualTo("카페");
    }

    @Test
    @DisplayName("AI 응답 patterns가 camelCase여도 파싱된다")
    void givenCamelCasePatterns_whenDeserialize_thenParseAiDraftResult() throws Exception {
        AiDraftResult result = objectMapper.readValue(camelCaseJson(), AiDraftResult.class);

        assertThat(result.patterns().timeOfDay()).isEqualTo("오후");
        assertThat(result.patterns().energyLevel()).isEqualTo(7);
        assertThat(result.patterns().spendingCategory()).isEqualTo("카페");
    }

    private String snakeCaseJson() {
        return """
                {
                  "generatedText": "오늘은 카페에 갔다.",
                  "emotions": [{"name": "만족", "score": 0.6}],
                  "happinessIndex": 72,
                  "activities": ["카페"],
                  "places": ["카페"],
                  "people": [{"name": "민수", "relation": "친구", "sentiment": "positive"}],
                  "iabCategories": ["음식>카페"],
                  "patterns": {
                    "time_of_day": "오후",
                    "energy_level": 7,
                    "social": "소수",
                    "spending": true,
                    "spending_category": "카페",
                    "weather": "맑음",
                    "health": "보통",
                    "sleep": "언급없음"
                  },
                  "moodSummary": "카페에서 보낸 날",
                  "tone": "회고적"
                }
                """;
    }

    private String camelCaseJson() {
        return """
                {
                  "generatedText": "오늘은 카페에 갔다.",
                  "emotions": [{"name": "만족", "score": 0.6}],
                  "happinessIndex": 72,
                  "activities": ["카페"],
                  "places": ["카페"],
                  "people": [{"name": "민수", "relation": "친구", "sentiment": "positive"}],
                  "iabCategories": ["음식>카페"],
                  "patterns": {
                    "timeOfDay": "오후",
                    "energyLevel": 7,
                    "social": "소수",
                    "spending": true,
                    "spendingCategory": "카페",
                    "weather": "맑음",
                    "health": "보통",
                    "sleep": "언급없음"
                  },
                  "moodSummary": "카페에서 보낸 날",
                  "tone": "회고적"
                }
                """;
    }
}
