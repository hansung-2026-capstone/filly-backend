package net.coboogie.diary.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "AI 일기 초안 생성 응답")
public record DiaryDraftResponse(
        @Schema(description = "AI가 작성한 일기 텍스트") String generatedText,
        @Schema(description = "AI 분석 결과") AiAnalysis aiAnalysis,
        @Schema(description = "업로드된 미디어 GCS URL 목록") List<String> mediaUrls
) {
    @Schema(description = "AI 분석 결과")
    public record AiAnalysis(
            @Schema(description = "감정 목록 (최대 5개, score 0.0~1.0)") List<AiDraftResult.EmotionScore> emotions,
            @Schema(description = "행복 지수 (0~100)") int happinessIndex,
            @Schema(description = "활동 태그") List<String> activities,
            @Schema(description = "장소 태그") List<String> places,
            @Schema(description = "등장 인물") List<AiDraftResult.PersonTag> people,
            @Schema(description = "IAB 취향 태그") List<String> iabCategories,
            @Schema(description = "일상 패턴") AiDraftResult.Patterns patterns,
            @Schema(description = "하루 한 줄 요약") String moodSummary,
            @Schema(description = "일기 톤 (회고적/실시간/계획형)") String tone
    ) {}
}
