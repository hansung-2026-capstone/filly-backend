package net.coboogie.diary.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

public record AiAnalysisResult(
        List<AiDraftResult.EmotionScore> emotions,
        @JsonAlias("happinessIndex")
        int happinessIndex,
        List<String> activities,
        List<String> places,
        List<AiDraftResult.PersonTag> people,
        @JsonAlias("iabCategories")
        List<String> iabCategories,
        AiDraftResult.Patterns patterns,
        @JsonAlias("moodSummary")
        String moodSummary,
        String tone
) {
    /** Converts this AI parsing DTO to the public draft response analysis DTO. */
    public DiaryDraftResponse.AiAnalysis toResponse() {
        return new DiaryDraftResponse.AiAnalysis(
                emotions,
                happinessIndex,
                activities,
                places,
                people,
                iabCategories,
                patterns,
                moodSummary,
                tone
        );
    }
}
