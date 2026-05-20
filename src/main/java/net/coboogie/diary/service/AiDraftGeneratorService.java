package net.coboogie.diary.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.coboogie.diary.dto.AiDraftResult;
import net.coboogie.diary.exception.AiDraftGenerationException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.MimeTypeUtils;

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
     * @param images             사용자가 제공한 사진 파일 목록 (null 허용)
     * @param voice              사용자가 제공한 음성 파일 (null 허용)
     * @param writtenAt          일기 작성 날짜
     * @param gender             사용자 성별 설정
     * @param ageGroup           사용자 나이대 설정
     * @param aiDraftTone        사용자 선호 초안 어투 설정
     * @return AI가 생성한 일기 텍스트 및 감정 분석 결과
     */
    public AiDraftResult generate(String textContent, List<MultipartFile> images,
                                  MultipartFile voice, LocalDate writtenAt,
                                  String gender, String ageGroup, String aiDraftTone) {
        boolean hasImages = images != null && !images.isEmpty();
        boolean hasVoice = voice != null && !voice.isEmpty();
        String userMessage = buildUserMessage(
                textContent, hasImages, hasVoice, writtenAt, gender, ageGroup, aiDraftTone);
        long startedAt = System.currentTimeMillis();
        log.info("Gemini diary draft request start writtenAt={} textLength={} imageCount={} hasVoice={}",
                writtenAt,
                textContent == null ? 0 : textContent.length(),
                images == null ? 0 : images.size(),
                hasVoice);

        String raw = chatClient.prompt()
                .user(u -> {
                    u.text(userMessage);
                    if (images != null) {
                        for (MultipartFile image : images) {
                            if (image != null && !image.isEmpty()) {
                                u.media(MimeTypeUtils.parseMimeType(image.getContentType()), image.getResource());
                            }
                        }
                    }
                    if (voice != null && !voice.isEmpty()) {
                        u.media(MimeTypeUtils.parseMimeType(voice.getContentType()), voice.getResource());
                    }
                })
                .call()
                .content();

        AiDraftResult result = parseWithFallback(raw);
        log.info("Gemini diary draft request complete elapsedMs={} generatedTextLength={} happinessIndex={}",
                System.currentTimeMillis() - startedAt,
                result.generatedText() == null ? 0 : result.generatedText().length(),
                result.happinessIndex());
        return result;
    }

    /**
     * 요청별 데이터를 조합하여 user 메시지를 구성한다.
     * 시스템 역할 정의는 AiConfig의 defaultSystem에서 처리하므로 여기서는 데이터만 담는다.
     */
    String buildUserMessage(String textContent, boolean hasImages,
                            boolean hasVoice, LocalDate writtenAt,
                            String gender, String ageGroup, String aiDraftTone) {
        return """
                [작성 날짜]
                %s

                [개인화 설정]
                성별: %s
                나이대: %s
                선호 어투: %s

                [개인화 적용 규칙]
                - 개인화 설정은 generatedText의 문체와 표현 강도에만 반영하세요.
                - 설정 없음인 항목은 generatedText에 반영하지 마세요.
                - 성별과 나이대는 본문에 직접 언급하지 마세요.
                - 입력에 없는 사건, 감정, 관계를 만들지 마세요.
                - emotions, happinessIndex, activities, places, people, iabCategories, patterns는 입력 내용 기준으로만 판단하세요.

                [텍스트 메모]
                %s

                [안내]
                - 입력된 이미지(사진) 및 음성 데이터를 직접 분석하여 일기 초안과 감정 분석 결과를 도출하세요.
                """.formatted(
                writtenAt.format(DATE_FORMATTER),
                displayPreference(gender),
                displayPreference(ageGroup),
                describeTone(aiDraftTone),
                orNone(textContent)
        );
    }

    private String displayPreference(String value) {
        if (value == null || value.isBlank()) {
            return "설정 없음";
        }
        String normalized = value.trim();
        if ("none".equals(normalized)) {
            return "설정 없음";
        }
        return switch (normalized) {
            case "male" -> "남성";
            case "female" -> "여성";
            default -> normalized;
        };
    }

    private String describeTone(String aiDraftTone) {
        if (aiDraftTone == null || aiDraftTone.isBlank()) {
            return "기본 자연스러운 일기체";
        }
        return switch (aiDraftTone.trim()) {
            case "calm" -> "차분하고 담백한 문체";
            case "warm" -> "따뜻하고 다정한 문체";
            case "lively" -> "밝고 생동감 있는 문체";
            case "literary" -> "약간 문학적이지만 과장 없는 문체";
            case "reflective" -> "생각과 감정을 차분히 돌아보는 문체";
            default -> "기본 자연스러운 일기체";
        };
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
            log.warn("Gemini 응답 JSON 파싱 실패. raw={}", abbreviate(raw, 1000), e);
            throw new AiDraftGenerationException("AI 응답을 파싱할 수 없습니다.", e);
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

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
