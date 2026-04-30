package net.coboogie.diary.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.coboogie.diary.dto.AiDraftResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Gemini를 사용하여 일기 초안을 생성하는 서비스.
 * <p>
 * 시스템 프롬프트(페르소나/출력 형식)는 AiConfig의 defaultSystem으로 등록되며,
 * 이 서비스는 요청별 사용자 데이터만 담은 user 메시지를 구성하여 호출한다.
 * Gemini 응답은 content()로 수령 후 방어 파싱하여 {@link AiDraftResult}로 변환한다.
 */
@Slf4j
@Service
public class AiDraftGeneratorService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public AiDraftGeneratorService(ChatClient chatClient,
                                   @Qualifier("aiObjectMapper") ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy년 M월 d일 EEEE", Locale.KOREAN);

    /**
     * 사용자 입력을 기반으로 AI 일기 초안을 생성한다.
     *
     * @param textContent        사용자가 직접 입력한 텍스트 메모 (null 허용)
     * @param imageCaptions      BLIP 모델로 추출된 이미지 장면 설명 목록
     * @param voiceTranscription Chirp STT로 전사된 음성 메모 (null 허용)
     * @param writtenAt          일기 작성 날짜
     * @return AI가 생성한 일기 텍스트 및 감정 분석 결과
     */
    public AiDraftResult generate(String textContent, List<String> imageCaptions,
                                  String voiceTranscription, LocalDate writtenAt) {
        String userMessage = buildUserMessage(textContent, imageCaptions, voiceTranscription, writtenAt);

        String raw = chatClient.prompt()
                .user(userMessage)
                .call()
                .content();

        return parseWithFallback(raw);
    }

    /**
     * 요청별 데이터를 조합하여 user 메시지를 구성한다.
     * 시스템 역할 정의는 AiConfig의 defaultSystem에서 처리하므로 여기서는 데이터만 담는다.
     */
    private String buildUserMessage(String textContent, List<String> imageCaptions,
                                    String voiceTranscription, LocalDate writtenAt) {
        return """
                [작성 날짜]
                %s

                [텍스트 메모]
                %s

                [음성 메모]
                %s

                [사진 속 장면]
                %s
                """.formatted(
                writtenAt.format(DATE_FORMATTER),
                orNone(textContent),
                orNone(voiceTranscription),
                imageCaptions.isEmpty() ? "(없음)" : String.join("\n", imageCaptions)
        );
    }

    /**
     * Gemini 응답에서 JSON을 추출하고 AiDraftResult로 파싱한다.
     * 응답에 코드블록이나 설명 문구가 섞여 있어도 JSON 부분만 파싱한다.
     */
    private AiDraftResult parseWithFallback(String raw) {
        String json = extractJson(raw);
        try {
            return objectMapper.readValue(json, AiDraftResult.class);
        } catch (JsonProcessingException e) {
            log.warn("Gemini 응답 JSON 파싱 실패. raw={}", raw, e);
            throw new IllegalStateException("AI 응답을 파싱할 수 없습니다.", e);
        }
    }

    /**
     * 응답 문자열에서 JSON 객체 부분만 추출한다.
     * ```json ... ``` 마크다운 코드블록을 제거하고 첫 { 부터 마지막 } 까지 반환한다.
     */
    private String extractJson(String raw) {
        raw = raw.replaceAll("(?s)```json\\s*", "").replaceAll("```", "").strip();
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
    }

    private String orNone(String value) {
        return (value == null || value.isBlank()) ? "(없음)" : value;
    }
}
