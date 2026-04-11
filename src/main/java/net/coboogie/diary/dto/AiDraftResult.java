package net.coboogie.diary.dto;

import java.util.List;

/**
 * Gemini로부터 구조화된 JSON 응답을 역직렬화하기 위한 내부 DTO.
 * <p>
 * Spring AI의 {@code ChatClient.call().entity(AiDraftResult.class)}가
 * AI 응답을 이 레코드로 자동 파싱한다.
 * 외부(프론트엔드)에 직접 노출되지 않으며, {@link DiaryDraftResponse}로 변환되어 반환된다.
 */
public record AiDraftResult(
        /** AI가 생성한 일기 본문 텍스트 */
        String generatedText,
        /** 감정 유형 (CALM / HAPPY / ANXIOUS / SAD / ANGRY) */
        String emotionType,
        /** 감정 점수 (0.0 ~ 1.0) */
        float emotionScore,
        /** 기분 지수 (1 ~ 10) */
        int moodIndex,
        /** AI가 감지한 핵심 키워드 목록 */
        List<String> detectedKeywords
) {
}
