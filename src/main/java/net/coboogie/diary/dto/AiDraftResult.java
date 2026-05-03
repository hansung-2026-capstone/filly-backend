package net.coboogie.diary.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

public record AiDraftResult(
        @JsonAlias("generatedText")
        String generatedText,
        List<EmotionScore> emotions,
        @JsonAlias("happinessIndex")
        int happinessIndex,
        List<String> activities,
        List<String> places,
        List<PersonTag> people,
        @JsonAlias("iabCategories")
        List<String> iabCategories,
        Patterns patterns,
        @JsonAlias("moodSummary")
        String moodSummary,
        String tone
) {
    public record EmotionScore(String name, float score) {}

    public record PersonTag(String name, String relation, String sentiment) {}

    public record Patterns(
            @JsonAlias("timeOfDay")
            String timeOfDay,
            @JsonAlias("energyLevel")
            int energyLevel,
            String social,
            boolean spending,
            @JsonAlias("spendingCategory")
            String spendingCategory,
            String weather,
            String health,
            String sleep
    ) {}
}
