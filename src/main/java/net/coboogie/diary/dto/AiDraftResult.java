package net.coboogie.diary.dto;

import java.util.List;

public record AiDraftResult(
        String generatedText,
        List<EmotionScore> emotions,
        int happinessIndex,
        List<String> activities,
        List<String> places,
        List<PersonTag> people,
        List<String> iabCategories,
        Patterns patterns,
        String moodSummary,
        String tone
) {
    public record EmotionScore(String name, float score) {}

    public record PersonTag(String name, String relation, String sentiment) {}

    public record Patterns(
            String timeOfDay,
            int energyLevel,
            String social,
            boolean spending,
            String spendingCategory,
            String weather,
            String health,
            String sleep
    ) {}
}
