package net.coboogie.diary.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiDraftGeneratorServiceTest {

    private final AiDraftGeneratorService sut = new AiDraftGeneratorService(null, new ObjectMapper());

    @Test
    @DisplayName("개인화 설정이 있으면 사용자 메시지에 문체 지시로 포함한다")
    void givenPreferences_whenBuildUserMessage_thenIncludePersonalizationRules() {
        // when
        String message = sut.buildUserMessage(
                "오늘 카페에 갔다",
                false,
                false,
                LocalDate.of(2026, 5, 14),
                "female",
                "20대",
                "warm"
        );

        // then
        assertThat(message)
                .contains("[개인화 설정]")
                .contains("성별: 여성")
                .contains("나이대: 20대")
                .contains("선호 어투: 따뜻하고 다정한 문체")
                .contains("성별과 나이대는 본문에 직접 언급하지 마세요")
                .contains("emotions, happinessIndex, activities, places, people, iabCategories, patterns")
                .contains("입력 내용 기준으로만 판단하세요")
                .contains("입력된 음성 데이터를 직접 분석하여");
    }

    @Test
    @DisplayName("개인화 기본값은 설정 없음과 기본 문체로 변환한다")
    void givenDefaultPreferences_whenBuildUserMessage_thenUseDefaultDescriptions() {
        // when
        String message = sut.buildUserMessage(
                "오늘은 날씨가 좋았다",
                false,
                false,
                LocalDate.of(2026, 5, 14),
                "none",
                "none",
                "none"
        );

        // then
        assertThat(message)
                .contains("성별: 설정 없음")
                .contains("나이대: 설정 없음")
                .contains("선호 어투: 기본 자연스러운 일기체")
                .contains("설정 없음인 항목은 generatedText에 반영하지 마세요");
    }
}
