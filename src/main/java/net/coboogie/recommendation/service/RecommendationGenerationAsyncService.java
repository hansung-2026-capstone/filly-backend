package net.coboogie.recommendation.service;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.coboogie.recommendation.exception.RecommendationGenerationException;
import net.coboogie.recommendation.repository.RecommendationDrawRepository;
import net.coboogie.recommendation.repository.RecommendationRepository;
import net.coboogie.user.exception.UserNotFoundException;
import net.coboogie.user.repository.UserRepository;
import net.coboogie.vo.RecommendationDrawVO;
import net.coboogie.vo.RecommendationVO;
import net.coboogie.vo.UserVO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 추천 카드 초기 생성을 백그라운드에서 처리하는 서비스.
 */
@Slf4j
@Service
public class RecommendationGenerationAsyncService {

    private static final int INITIAL_CARD_COUNT = 3;

    private final UserRepository userRepository;
    private final RecommendationDrawRepository recommendationDrawRepository;
    private final RecommendationRepository recommendationRepository;
    private final RecommendationCategorySelector recommendationCategorySelector;
    private final ChatClient recommendationChatClient;
    private final ObjectMapper objectMapper;
    private final ObjectMapper aiObjectMapper;
    private final TransactionTemplate transactionTemplate;

    /**
     * RecommendationGenerationAsyncService 생성자.
     */
    public RecommendationGenerationAsyncService(
            UserRepository userRepository,
            RecommendationDrawRepository recommendationDrawRepository,
            RecommendationRepository recommendationRepository,
            RecommendationCategorySelector recommendationCategorySelector,
            @Qualifier("recommendationChatClient") ChatClient recommendationChatClient,
            ObjectMapper objectMapper,
            @Qualifier("aiObjectMapper") ObjectMapper aiObjectMapper,
            PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.recommendationDrawRepository = recommendationDrawRepository;
        this.recommendationRepository = recommendationRepository;
        this.recommendationCategorySelector = recommendationCategorySelector;
        this.recommendationChatClient = recommendationChatClient;
        this.objectMapper = objectMapper;
        this.aiObjectMapper = aiObjectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * PENDING 추천 뽑기 세션에 초기 추천 카드 3장을 생성한다.
     *
     * @param drawId  추천 뽑기 세션 ID
     * @param userId  사용자 ID
     * @param profile 추천 프로필
     */
    @Async
    public void generateInitialCards(Long drawId, Long userId, RecommendationProfile profile) {
        long startedAt = System.currentTimeMillis();
        if (!isPending(drawId)) {
            log.info("recommendation generation skipped drawId={} reason=not_pending", drawId);
            return;
        }

        try {
            List<String> categories = recommendationCategorySelector.selectInitialMainCategories(profile);
            List<AiRecommendationCard> generatedCards = generateCards(profile, categories);
            saveGeneratedCards(drawId, userId, profile, categories, generatedCards);
            log.info("recommendation generation complete drawId={} elapsedMs={}",
                    drawId, System.currentTimeMillis() - startedAt);
        } catch (Exception e) {
            markFailed(drawId);
            log.error("recommendation generation failed drawId={} elapsedMs={}",
                    drawId, System.currentTimeMillis() - startedAt, e);
        }
    }

    private boolean isPending(Long drawId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status ->
                recommendationDrawRepository.findById(drawId)
                        .map(draw -> draw.getStatus() == RecommendationDrawVO.Status.PENDING)
                        .orElse(false)));
    }

    private void saveGeneratedCards(Long drawId, Long userId, RecommendationProfile profile,
                                    List<String> categories, List<AiRecommendationCard> generatedCards) {
        transactionTemplate.executeWithoutResult(status -> {
            RecommendationDrawVO draw = recommendationDrawRepository.findById(drawId)
                    .orElseThrow(() -> new NoSuchElementException("추천 뽑기 세션을 찾을 수 없습니다: " + drawId));
            if (draw.getStatus() != RecommendationDrawVO.Status.PENDING) {
                return;
            }
            UserVO user = userRepository.findById(userId)
                    .orElseThrow(() -> new UserNotFoundException(userId));
            saveCards(draw, user, profile, categories, generatedCards);
            draw.setStatus(RecommendationDrawVO.Status.ACTIVE);
        });
    }

    private void markFailed(Long drawId) {
        transactionTemplate.executeWithoutResult(status ->
                recommendationDrawRepository.findById(drawId).ifPresent(draw -> {
                    if (draw.getStatus() == RecommendationDrawVO.Status.PENDING) {
                        draw.setStatus(RecommendationDrawVO.Status.FAILED);
                    }
                }));
    }

    private List<AiRecommendationCard> generateCards(RecommendationProfile profile, List<String> categories) {
        String userMessage = """
                mode: INITIAL
                card_count: 3
                categories: %s

                recommendation_requirements:
                - 각 카드 reason에는 user_profile의 "우선 반영할 개인 습관 후보" 또는 "일상 패턴" 중 최소 1개를 구체적으로 언급하세요.
                - 개인 습관 후보가 "없음"이 아니면, IAB 카테고리보다 개인 습관 후보를 먼저 근거로 삼으세요.
                - title은 장르명이 아니라 실제 대상 이름이어야 합니다. 예: "음악" 금지, "검정치마 - 기다린 만큼, 더" 허용.
                - description은 실행 시간대나 상황을 포함해야 합니다.
                - 같은 추천 대상이나 같은 문장 구조를 반복하지 마세요.

                user_profile:
                %s
                """.formatted(String.join(", ", categories), profile.toPromptSummary());
        AiRecommendationResponse response = callGemini(userMessage);
        return response.cards() == null ? List.of() : response.cards();
    }

    private AiRecommendationResponse callGemini(String userMessage) {
        String raw = recommendationChatClient.prompt()
                .user(userMessage)
                .call()
                .content();
        String json = extractJson(raw);
        try {
            return aiObjectMapper.readValue(json, AiRecommendationResponse.class);
        } catch (JsonProcessingException e) {
            log.warn("추천 AI 응답 파싱 실패. raw={}", abbreviate(raw, 1000), e);
            throw new RecommendationGenerationException("추천 AI 응답을 파싱할 수 없습니다.", e);
        }
    }

    private void saveCards(RecommendationDrawVO draw, UserVO user, RecommendationProfile profile,
                           List<String> categories, List<AiRecommendationCard> generatedCards) {
        for (int i = 0; i < INITIAL_CARD_COUNT; i++) {
            String category = categories.get(i);
            AiRecommendationCard generatedCard = i < generatedCards.size()
                    ? generatedCards.get(i)
                    : fallbackCard(category, recommendationCategorySelector.selectSubCategory(profile, category));
            saveCard(draw, user, profile, generatedCard, category, i + 1);
        }
    }

    private RecommendationVO saveCard(RecommendationDrawVO draw, UserVO user, RecommendationProfile profile,
                                      AiRecommendationCard generatedCard, String fallbackCategory, int cardIndex) {
        String mainCategory = fallbackCategory;
        String subCategory = valueOrDefault(
                generatedCard.subCategory(),
                recommendationCategorySelector.selectSubCategory(profile, mainCategory)
        );
        String contentType = valueOrDefault(generatedCard.contentType(), "ACTIVITY");
        String searchKeyword = valueOrDefault(generatedCard.searchKeyword(), mainCategory + " 추천");
        RecommendationContent content = new RecommendationContent(
                valueOrDefault(generatedCard.title(), "오늘의 추천"),
                valueOrDefault(generatedCard.description(), "지금의 취향과 분위기에 맞춘 추천입니다."),
                searchKeyword,
                buildLinkUrl(contentType, searchKeyword)
        );

        return recommendationRepository.save(RecommendationVO.builder()
                .draw(draw)
                .user(user)
                .roundNo(1)
                .iabMainCategory(mainCategory)
                .iabSubCategory(subCategory)
                .contentType(contentType)
                .contentRef(toJson(content))
                .reason(valueOrDefault(generatedCard.reason(), "최근 일기 분석 데이터와 취향 태그를 바탕으로 추천했습니다."))
                .cardIndex(cardIndex)
                .revealed(false)
                .status(RecommendationVO.Status.ACTIVE)
                .generationType(RecommendationVO.GenerationType.INITIAL)
                .build());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RecommendationGenerationException("추천 데이터를 JSON으로 저장할 수 없습니다.", e);
        }
    }

    private AiRecommendationCard fallbackCard(String category, String subCategory) {
        return new AiRecommendationCard(
                category,
                subCategory,
                "ACTIVITY",
                subCategory + " 30분 루틴",
                "오늘 안에 바로 끝낼 수 있게 30분만 잡고 작게 시작해보세요. 준비물이 거의 없는 방식으로 시작하면 부담 없이 기분 전환을 만들 수 있습니다.",
                subCategory + " 30분 루틴",
                "최근 기록된 취향과 일상 패턴을 바탕으로 부담이 낮은 실행형 추천을 선택했습니다."
        );
    }

    private String buildLinkUrl(String contentType, String searchKeyword) {
        boolean notMusic = !"MUSIC".equalsIgnoreCase(valueOrDefault(contentType, ""));
        if (notMusic || searchKeyword == null || searchKeyword.isBlank()) {
            return null;
        }
        return "https://www.youtube.com/results?search_query="
                + URLEncoder.encode(searchKeyword, StandardCharsets.UTF_8);
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String extractJson(String raw) {
        String stripped = raw.replaceAll("(?s)```json\\s*", "").replaceAll("```", "").strip();
        int start = stripped.indexOf('{');
        int end = stripped.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return stripped.substring(start, end + 1);
        }
        return stripped;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private record RecommendationContent(String title, String description, String searchKeyword, String linkUrl) {
    }

    private record AiRecommendationResponse(List<AiRecommendationCard> cards) {
    }

    private record AiRecommendationCard(
            String category,
            @JsonAlias("subCategory") String subCategory,
            @JsonAlias("contentType") String contentType,
            String title,
            String description,
            @JsonAlias("searchKeyword") String searchKeyword,
            String reason
    ) {
    }
}
