package net.coboogie.diary.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record AiDraftTextResult(
        @JsonAlias("generatedText")
        String generatedText
) {
}
