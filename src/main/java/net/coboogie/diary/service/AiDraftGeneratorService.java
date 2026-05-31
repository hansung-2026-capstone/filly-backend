package net.coboogie.diary.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.coboogie.diary.dto.AiAnalysisResult;
import net.coboogie.diary.dto.AiDraftResult;
import net.coboogie.diary.dto.AiDraftTextResult;
import net.coboogie.diary.dto.DiaryDraftResponse;
import net.coboogie.diary.exception.AiDraftGenerationException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
    private final ChatClient diaryDraftChatClient;
    private final ChatClient diaryAnalysisChatClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public AiDraftGeneratorService(@Qualifier("chatClient") ChatClient chatClient,
                                   @Qualifier("diaryDraftChatClient") ChatClient diaryDraftChatClient,
                                   @Qualifier("diaryAnalysisChatClient") ChatClient diaryAnalysisChatClient,
                                   @Qualifier("aiObjectMapper") ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.diaryDraftChatClient = diaryDraftChatClient;
        this.diaryAnalysisChatClient = diaryAnalysisChatClient;
        this.objectMapper = objectMapper;
    }

    AiDraftGeneratorService(ChatClient chatClient, ObjectMapper objectMapper) {
        this(chatClient, chatClient, chatClient, objectMapper);
    }

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy년 M월 d일 EEEE", Locale.KOREAN);

    /**
     * Generate only the user-visible diary draft text. Analysis is intentionally excluded
     * so the draft API can return quickly and saved diary analysis can run asynchronously.
     */
    public String generateDraftTextMultimodal(String textContent, List<MultipartFile> images,
                                              MultipartFile voice, LocalDate writtenAt,
                                              String gender, String ageGroup, String aiDraftTone) {
        String userMessage = buildDraftUserMessage(textContent, null, voice,
                writtenAt, gender, ageGroup, aiDraftTone);
        long mediaStartedAt = System.currentTimeMillis();
        Media[] media = buildMedia(null, voice);
        long mediaBuildElapsedMs = System.currentTimeMillis() - mediaStartedAt;
        long startedAt = System.currentTimeMillis();
        log.info("Gemini diary draft text request start writtenAt={} textLength={} mediaCount={} mediaBuildMs={} hasVoice={}",
                writtenAt,
                textContent == null ? 0 : textContent.length(),
                media.length,
                mediaBuildElapsedMs,
                voice != null && !voice.isEmpty());

        String raw = diaryDraftChatClient.prompt()
                .user(user -> {
                    user.text(userMessage);
                    if (media.length > 0) {
                        user.media(media);
                    }
                })
                .call()
                .content();

        AiDraftTextResult result = parseDraftTextWithFallback(raw);
        log.info("Gemini diary draft text request complete elapsedMs={} generatedTextLength={}",
                System.currentTimeMillis() - startedAt,
                result.generatedText() == null ? 0 : result.generatedText().length());
        return result.generatedText();
    }

    /**
     * Analyze a saved diary without regenerating the draft text.
     */
    public DiaryDraftResponse.AiAnalysis analyzeDiaryMultimodal(String textContent, List<MultipartFile> images,
                                                                MultipartFile voice, LocalDate writtenAt,
                                                                String gender, String ageGroup,
                                                                String aiDraftTone) {
        String userMessage = buildAnalysisUserMessage(textContent, null, voice,
                writtenAt, gender, ageGroup, aiDraftTone);
        long mediaStartedAt = System.currentTimeMillis();
        Media[] media = buildMedia(null, voice);
        long mediaBuildElapsedMs = System.currentTimeMillis() - mediaStartedAt;
        long startedAt = System.currentTimeMillis();
        log.info("Gemini diary analysis request start writtenAt={} textLength={} mediaCount={} mediaBuildMs={} hasVoice={}",
                writtenAt,
                textContent == null ? 0 : textContent.length(),
                media.length,
                mediaBuildElapsedMs,
                voice != null && !voice.isEmpty());

        String raw = diaryAnalysisChatClient.prompt()
                .user(user -> {
                    user.text(userMessage);
                    if (media.length > 0) {
                        user.media(media);
                    }
                })
                .call()
                .content();

        AiAnalysisResult result = parseAnalysisWithFallback(raw);
        log.info("Gemini diary analysis request complete elapsedMs={} happinessIndex={}",
                System.currentTimeMillis() - startedAt,
                result.happinessIndex());
        return result.toResponse();
    }

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
     * 사용자 입력 원본(텍스트/이미지/음성)을 Gemini 멀티모달 입력으로 전달하여 일기 초안을 생성한다.
     * v2 초안 생성에서는 STT와 BLIP 전처리를 거치지 않는다.
     */
    public AiDraftResult generateMultimodal(String textContent, List<MultipartFile> images,
                                            MultipartFile voice, LocalDate writtenAt,
                                            String gender, String ageGroup, String aiDraftTone) {
        String userMessage = buildMultimodalUserMessage(textContent, images, voice,
                writtenAt, gender, ageGroup, aiDraftTone);
        Media[] media = buildMedia(images, voice);
        long startedAt = System.currentTimeMillis();
        log.info("Gemini multimodal diary draft request start writtenAt={} textLength={} mediaCount={} hasVoice={}",
                writtenAt,
                textContent == null ? 0 : textContent.length(),
                media.length,
                voice != null && !voice.isEmpty());

        String raw = chatClient.prompt()
                .user(user -> {
                    user.text(userMessage);
                    if (media.length > 0) {
                        user.media(media);
                    }
                })
                .call()
                .content();

        AiDraftResult result = parseWithFallback(raw);
        log.info("Gemini multimodal diary draft request complete elapsedMs={} generatedTextLength={} happinessIndex={}",
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

    String buildMultimodalUserMessage(String textContent, List<MultipartFile> images,
                                      MultipartFile voice, LocalDate writtenAt,
                                      String gender, String ageGroup, String aiDraftTone) {
        int imageCount = images == null ? 0
                : (int) images.stream().filter(image -> image != null && !image.isEmpty()).count();
        boolean hasVoice = voice != null && !voice.isEmpty();
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

                [입력 해석 규칙]
                - 첨부 오디오는 사용자의 음성 메모입니다. 전사한 뒤 텍스트 메모와 같은 비중으로 사용하세요.
                - 첨부 이미지는 사용자가 남긴 사진입니다. 사진 속 명확한 장면, 장소, 활동만 일기 단서로 사용하세요.
                - 이미지나 오디오에서 불확실한 정보는 단정하지 마세요.
                - 텍스트, 오디오, 이미지를 종합해 기존 응답 JSON 스키마와 동일하게 반환하세요.

                [텍스트 메모]
                %s

                [첨부 현황]
                이미지: %d장
                음성: %s
                """.formatted(
                writtenAt.format(DATE_FORMATTER),
                displayPreference(gender),
                displayPreference(ageGroup),
                describeTone(aiDraftTone),
                orNone(textContent),
                imageCount,
                hasVoice ? "있음" : "없음"
        );
    }

    String buildDraftUserMessage(String textContent, List<MultipartFile> images,
                                 MultipartFile voice, LocalDate writtenAt,
                                 String gender, String ageGroup, String aiDraftTone) {
        boolean hasVoice = voice != null && !voice.isEmpty();
        return """
                [Written date]
                %s

                [User settings]
                gender: %s
                age group: %s
                preferred tone: %s

                [Input]
                text memo:
                %s

                voice: %s

                [Rules]
                - Return only generatedText JSON.
                - Write generatedText in Korean.
                - Use user settings only for style.
                - Do not include emotion/category/pattern analysis.
                """.formatted(
                writtenAt.format(DATE_FORMATTER),
                displayPreference(gender),
                displayPreference(ageGroup),
                describeTone(aiDraftTone),
                orNone(textContent),
                hasVoice ? "present" : "none"
        );
    }

    String buildAnalysisUserMessage(String textContent, List<MultipartFile> images,
                                    MultipartFile voice, LocalDate writtenAt,
                                    String gender, String ageGroup, String aiDraftTone) {
        boolean hasVoice = voice != null && !voice.isEmpty();
        return """
                [Written date]
                %s

                [User settings]
                gender: %s
                age group: %s
                preferred tone: %s

                [Diary text]
                %s

                [Attachments]
                voice: %s

                [Rules]
                - Return only analysis JSON.
                - Do not generate or rewrite diary text.
                - Use the diary text as the primary source.
                """.formatted(
                writtenAt.format(DATE_FORMATTER),
                displayPreference(gender),
                displayPreference(ageGroup),
                describeTone(aiDraftTone),
                orNone(textContent),
                hasVoice ? "present" : "none"
        );
    }

    private Media[] buildMedia(List<MultipartFile> images, MultipartFile voice) {
        List<Media> media = new ArrayList<>();
        if (images != null) {
            for (MultipartFile image : images) {
                if (image != null && !image.isEmpty()) {
                    media.add(toMedia(image));
                }
            }
        }
        if (voice != null && !voice.isEmpty()) {
            media.add(toMedia(voice));
        }
        return media.toArray(Media[]::new);
    }

    private Media toMedia(MultipartFile file) {
        try {
            String filename = file.getOriginalFilename();
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
            return Media.builder()
                    .mimeType(MimeTypeUtils.parseMimeType(file.getContentType()))
                    .data(resource)
                    .name(filename)
                    .build();
        } catch (IOException e) {
            throw new AiDraftGenerationException("첨부 파일을 AI 입력으로 변환할 수 없습니다: "
                    + file.getOriginalFilename(), e);
        }
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
    private AiDraftTextResult parseDraftTextWithFallback(String raw) {
        String json = extractJson(raw);
        try {
            return objectMapper.readValue(json, AiDraftTextResult.class);
        } catch (JsonProcessingException e) {
            log.warn("Gemini draft text JSON parsing failed. raw={}", abbreviate(raw, 1000), e);
            throw new AiDraftGenerationException("AI draft response could not be parsed.", e);
        }
    }

    private AiAnalysisResult parseAnalysisWithFallback(String raw) {
        String json = extractJson(raw);
        try {
            return objectMapper.readValue(json, AiAnalysisResult.class);
        } catch (JsonProcessingException e) {
            log.warn("Gemini analysis JSON parsing failed. raw={}", abbreviate(raw, 1000), e);
            throw new AiDraftGenerationException("AI analysis response could not be parsed.", e);
        }
    }

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
