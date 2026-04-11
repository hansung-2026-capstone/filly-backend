package net.coboogie.diary.service;

import lombok.RequiredArgsConstructor;
import net.coboogie.diary.dto.AiDraftResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Gemini를 사용하여 일기 초안을 생성하는 서비스.
 * <p>
 * 사용자 입력(텍스트, 이미지 캡션)을 바탕으로 프롬프트를 구성하고,
 * Spring AI의 {@code ChatClient}를 통해 Gemini 모델을 호출한다.
 * AI 응답은 {@link AiDraftResult}로 구조화되어 반환된다.
 */
@Service
@RequiredArgsConstructor
public class AiDraftGeneratorService {

    private final ChatClient chatClient;

    /**
     * 사용자 입력을 기반으로 AI 일기 초안을 생성한다.
     *
     * @param textContent        사용자가 직접 입력한 텍스트 메모 (null 허용)
     * @param imageCaptions      이미지 분석으로 추출된 설명 목록
     * @param voiceTranscription Chirp STT로 전사된 음성 메모 (null 허용)
     * @param writtenAt          일기 작성 날짜
     * @return AI가 생성한 일기 텍스트 및 감정 분석 결과
     */
    public AiDraftResult generate(String textContent, List<String> imageCaptions, String voiceTranscription, LocalDate writtenAt) {
        String prompt = buildPrompt(textContent, imageCaptions, voiceTranscription, writtenAt);

        // Spring AI structured output: AI 응답을 AiDraftResult 레코드로 자동 역직렬화
        return chatClient.prompt()
                .user(prompt)
                .call()
                .entity(AiDraftResult.class);
    }

    /**
     * 입력 데이터를 조합하여 Gemini에 전달할 프롬프트를 구성한다.
     * JSON 형식으로 응답하도록 명시하여 {@link AiDraftResult} 파싱 신뢰도를 높인다.
     */
    private String buildPrompt(String textContent, List<String> imageCaptions, String voiceTranscription, LocalDate writtenAt) {
        StringBuilder sb = new StringBuilder();
        sb.append("당신은 일기를 대신 써주는 AI입니다.\n");
        sb.append("아래 정보를 바탕으로 따뜻하고 감성적인 한국어 일기를 2~3 문단으로 작성해주세요.\n\n");

        if (textContent != null && !textContent.isBlank()) {
            sb.append("사용자 메모: ").append(textContent).append("\n");
        }
        if (voiceTranscription != null && !voiceTranscription.isBlank()) {
            sb.append("음성 메모: ").append(voiceTranscription).append("\n");
        }
        if (!imageCaptions.isEmpty()) {
            sb.append("이미지 설명: ").append(String.join(", ", imageCaptions)).append("\n");
        }
        sb.append("날짜: ").append(writtenAt).append("\n\n");

        sb.append("다음 JSON 형식으로만 응답하세요. 다른 텍스트는 포함하지 마세요:\n");
        sb.append("{\n");
        sb.append("  \"generatedText\": \"작성된 일기 텍스트\",\n");
        sb.append("  \"emotionType\": \"HAPPY\",\n");
        sb.append("  \"emotionScore\": 0.85,\n");
        sb.append("  \"moodIndex\": 8,\n");
        sb.append("  \"detectedKeywords\": [\"키워드1\", \"키워드2\", \"키워드3\"]\n");
        sb.append("}\n");
        sb.append("emotionType은 CALM, HAPPY, ANXIOUS, SAD, ANGRY 중 하나.\n");
        sb.append("emotionScore는 0.0~1.0, moodIndex는 1~10 정수.");

        return sb.toString();
    }
}
