package net.coboogie.diary.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * {@code POST /api/v1/diaries/draft} 성공 응답 DTO.
 * <p>
 * AI가 생성한 초안 텍스트, 감정 분석 결과, GCS에 업로드된 미디어 URL 목록을 담아 반환한다.
 * 초안은 DB에 저장되지 않으며, 사용자가 내용을 확인·수정한 뒤
 * {@code POST /api/v1/diaries}로 최종 저장한다.
 */
@Schema(description = "AI 일기 초안 생성 응답")
public record DiaryDraftResponse(
        @Schema(description = "AI가 작성한 일기 텍스트") String generatedText,
        @Schema(description = "AI 감정 분석 결과") AiAnalysis aiAnalysis,
        @Schema(description = "업로드된 미디어 GCS URL 목록") List<String> mediaUrls
) {
    /**
     * AI가 분석한 감정 정보.
     * 최종 저장({@code POST /diaries}) 시 그대로 전달하면 DB에 기록된다.
     */
    @Schema(description = "감정 분석 결과")
    public record AiAnalysis(
            @Schema(description = "감정 유형 (CALM/HAPPY/ANXIOUS/SAD/ANGRY)") String emotionType,
            @Schema(description = "감정 점수 (0.0 ~ 1.0)") float emotionScore,
            @Schema(description = "기분 지수 (1 ~ 10)") int moodIndex,
            @Schema(description = "감지된 키워드 목록") List<String> detectedKeywords
    ) {
    }
}
