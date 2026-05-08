package net.coboogie.recommendation.service;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.coboogie.recommendation.dto.RecommendationCardResponse;
import net.coboogie.recommendation.dto.RecommendationDrawResponse;
import net.coboogie.recommendation.dto.RecommendationRevealResponse;
import net.coboogie.recommendation.dto.RecommendationShuffleResponse;
import net.coboogie.recommendation.exception.RecommendationGenerationException;
import net.coboogie.recommendation.exception.RecommendationLimitExceededException;
import net.coboogie.recommendation.repository.RecommendationDrawRepository;
import net.coboogie.recommendation.repository.RecommendationRepository;
import net.coboogie.user.exception.UserNotFoundException;
import net.coboogie.user.repository.UserRepository;
import net.coboogie.vo.RecommendationDrawVO;
import net.coboogie.vo.RecommendationVO;
import net.coboogie.vo.UserVO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * 뽑기형 추천 API 비즈니스 로직 서비스.
 */
@Slf4j
@Service
public class RecommendationService {

    private static final int INITIAL_CARD_COUNT = 3;
    private static final int DAILY_SHUFFLE_LIMIT = 3;
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final RecommendationDrawRepository recommendationDrawRepository;
    private final RecommendationRepository recommendationRepository;
    private final RecommendationProfileBuilder recommendationProfileBuilder;
    private final RecommendationCategorySelector recommendationCategorySelector;
    private final ChatClient recommendationChatClient;
    private final ObjectMapper objectMapper;
    private final ObjectMapper aiObjectMapper;

    /**
     * RecommendationService 생성자.
     */
    public RecommendationService(
            UserRepository userRepository,
            RecommendationDrawRepository recommendationDrawRepository,
            RecommendationRepository recommendationRepository,
            RecommendationProfileBuilder recommendationProfileBuilder,
            RecommendationCategorySelector recommendationCategorySelector,
            @Qualifier("recommendationChatClient") ChatClient recommendationChatClient,
            ObjectMapper objectMapper,
            @Qualifier("aiObjectMapper") ObjectMapper aiObjectMapper) {
        this.userRepository = userRepository;
        this.recommendationDrawRepository = recommendationDrawRepository;
        this.recommendationRepository = recommendationRepository;
        this.recommendationProfileBuilder = recommendationProfileBuilder;
        this.recommendationCategorySelector = recommendationCategorySelector;
        this.recommendationChatClient = recommendationChatClient;
        this.objectMapper = objectMapper;
        this.aiObjectMapper = aiObjectMapper;
    }

    /**
     * 추천 뽑기 세션을 시작하고 서로 다른 카테고리 카드 3장을 생성한다.
     *
     * @param userId 사용자 ID
     * @return 추천 뽑기 응답
     */
    @Transactional
    public RecommendationDrawResponse draw(Long userId) {
        UserVO user = findUser(userId);
        RecommendationProfile profile = recommendationProfileBuilder.build(userId);
        String profileSnapshot = toJson(profile);

        RecommendationDrawVO draw = recommendationDrawRepository.save(RecommendationDrawVO.builder()
                .user(user)
                .currentRound(1)
                .sourcePeriod(profile.sourcePeriod())
                .profileSnapshot(profileSnapshot)
                .status(RecommendationDrawVO.Status.ACTIVE)
                .build());

        List<String> categories = recommendationCategorySelector.selectInitialMainCategories(profile);
        List<AiRecommendationCard> generatedCards = generateCards(profile, categories);
        List<RecommendationVO> cards = saveCards(draw, user, profile, categories, generatedCards);

        return new RecommendationDrawResponse(draw.getId(), draw.getCurrentRound(), toBackCards(cards));
    }

    /**
     * 선택한 추천 카드를 공개하고 카드 내용을 반환한다.
     *
     * @param userId 사용자 ID
     * @param drawId 추천 뽑기 세션 ID
     * @param cardId 추천 카드 ID
     * @return 공개된 추천 카드 응답
     */
    @Transactional
    public RecommendationRevealResponse reveal(Long userId, Long drawId, Long cardId) {
        RecommendationVO card = recommendationRepository.findByIdAndDraw_IdAndUser_Id(cardId, drawId, userId)
                .orElseThrow(() -> new NoSuchElementException("추천 카드를 찾을 수 없습니다: " + cardId));
        validateRevealWindow(drawId);
        if (card.getStatus() != RecommendationVO.Status.ACTIVE || Boolean.TRUE.equals(card.getRevealed())) {
            throw new IllegalArgumentException("이미 공개되었거나 선택할 수 없는 추천 카드입니다.");
        }

        card.setStatus(RecommendationVO.Status.REVEALED);
        card.setRevealed(true);

        return toRevealResponse(card);
    }

    /**
     * 이미 본 카드 1장을 제외하고, 안 본 카드 2장과 새 카드 1장으로 다음 라운드를 구성한다.
     *
     * @param userId 사용자 ID
     * @param drawId 추천 뽑기 세션 ID
     * @return shuffle 응답
     */
    @Transactional
    public RecommendationShuffleResponse shuffle(Long userId, Long drawId) {
        validateDailyShuffleLimit(userId);

        RecommendationDrawVO draw = recommendationDrawRepository.findByIdAndUser_Id(drawId, userId)
                .orElseThrow(() -> new NoSuchElementException("추천 뽑기 세션을 찾을 수 없습니다: " + drawId));
        if (draw.getStatus() != RecommendationDrawVO.Status.ACTIVE) {
            throw new IllegalArgumentException("진행 중인 추천 뽑기 세션이 아닙니다.");
        }

        List<RecommendationVO> activeCards = recommendationRepository
                .findByDraw_IdAndStatusOrderByCardIndexAsc(drawId, RecommendationVO.Status.ACTIVE);
        if (activeCards.size() != 2) {
            throw new IllegalArgumentException("다른 추천 보기는 카드 1장을 공개한 뒤 사용할 수 있습니다.");
        }

        RecommendationProfile profile = parseProfile(draw.getProfileSnapshot());
        RecommendationVO lastRevealed = findLastRevealedCard(drawId);
        Set<String> excludedCategories = collectExcludedCategories(activeCards, lastRevealed);
        String newCategory = recommendationCategorySelector.selectReplacementMainCategory(profile, excludedCategories);
        AiRecommendationCard generatedCard = generateCard(profile, newCategory);
        int nextRound = draw.getCurrentRound() + 1;
        int cardIndex = findEmptyCardIndex(activeCards);

        RecommendationVO newCard = saveCard(
                draw,
                draw.getUser(),
                profile,
                generatedCard,
                newCategory,
                cardIndex,
                nextRound,
                RecommendationVO.GenerationType.SHUFFLE
        );

        draw.setCurrentRound(nextRound);
        List<RecommendationVO> nextCards = new ArrayList<>(activeCards);
        nextCards.add(newCard);
        nextCards.sort((a, b) -> Integer.compare(a.getCardIndex(), b.getCardIndex()));

        return new RecommendationShuffleResponse(draw.getId(), nextRound, toBackCards(nextCards));
    }

    /**
     * 사용자가 공개한 추천 카드 이력을 조회한다.
     *
     * @param userId 사용자 ID
     * @return 공개된 추천 카드 목록
     */
    @Transactional(readOnly = true)
    public List<RecommendationRevealResponse> getHistory(Long userId) {
        return recommendationRepository
                .findByUser_IdAndStatusOrderByUpdatedAtDesc(userId, RecommendationVO.Status.REVEALED)
                .stream()
                .map(this::toRevealResponse)
                .toList();
    }

    private UserVO findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private List<RecommendationVO> saveCards(RecommendationDrawVO draw, UserVO user, RecommendationProfile profile,
                                             List<String> categories, List<AiRecommendationCard> generatedCards) {
        List<RecommendationVO> cards = new ArrayList<>();
        for (int i = 0; i < INITIAL_CARD_COUNT; i++) {
            String category = categories.get(i);
            AiRecommendationCard generatedCard = i < generatedCards.size()
                    ? generatedCards.get(i)
                    : fallbackCard(category, recommendationCategorySelector.selectSubCategory(profile, category));
            cards.add(saveCard(draw, user, profile, generatedCard, category, i + 1, 1,
                    RecommendationVO.GenerationType.INITIAL));
        }
        return cards;
    }

    private RecommendationVO saveCard(RecommendationDrawVO draw, UserVO user, RecommendationProfile profile,
                                      AiRecommendationCard generatedCard, String fallbackCategory, int cardIndex,
                                      int roundNo, RecommendationVO.GenerationType generationType) {
        String mainCategory = fallbackCategory;
        String subCategory = valueOrDefault(
                generatedCard.subCategory(),
                recommendationCategorySelector.selectSubCategory(profile, mainCategory)
        );
        RecommendationContent content = new RecommendationContent(
                valueOrDefault(generatedCard.title(), "오늘의 추천"),
                valueOrDefault(generatedCard.description(), "지금의 취향과 분위기에 맞춘 추천입니다."),
                valueOrDefault(generatedCard.searchKeyword(), mainCategory + " 추천")
        );

        return recommendationRepository.save(RecommendationVO.builder()
                .draw(draw)
                .user(user)
                .roundNo(roundNo)
                .iabMainCategory(mainCategory)
                .iabSubCategory(subCategory)
                .contentType(valueOrDefault(generatedCard.contentType(), "ACTIVITY"))
                .contentRef(toJson(content))
                .reason(valueOrDefault(generatedCard.reason(), "최근 일기 분석 데이터와 취향 태그를 바탕으로 추천했습니다."))
                .cardIndex(cardIndex)
                .revealed(false)
                .status(RecommendationVO.Status.ACTIVE)
                .generationType(generationType)
                .build());
    }

    private List<AiRecommendationCard> generateCards(RecommendationProfile profile, List<String> categories) {
        String userMessage = """
                mode: INITIAL
                card_count: 3
                categories: %s

                user_profile:
                %s
                """.formatted(String.join(", ", categories), profile.toPromptSummary());
        AiRecommendationResponse response = callGemini(userMessage);
        return response.cards() == null ? List.of() : response.cards();
    }

    private AiRecommendationCard generateCard(RecommendationProfile profile, String category) {
        String userMessage = """
                mode: SHUFFLE
                card_count: 1
                category: %s

                user_profile:
                %s
                """.formatted(category, profile.toPromptSummary());
        AiRecommendationResponse response = callGemini(userMessage);
        if (response.cards() == null || response.cards().isEmpty()) {
            return fallbackCard(category, recommendationCategorySelector.selectSubCategory(profile, category));
        }
        return response.cards().get(0);
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

    private RecommendationVO findLastRevealedCard(Long drawId) {
        List<RecommendationVO> revealedCards = recommendationRepository
                .findByDraw_IdAndStatusOrderByUpdatedAtDesc(drawId, RecommendationVO.Status.REVEALED);
        if (revealedCards.isEmpty()) {
            throw new IllegalArgumentException("다른 추천 보기는 카드 1장을 공개한 뒤 사용할 수 있습니다.");
        }
        return revealedCards.get(0);
    }

    private void validateRevealWindow(Long drawId) {
        List<RecommendationVO> activeCards = recommendationRepository
                .findByDraw_IdAndStatusOrderByCardIndexAsc(drawId, RecommendationVO.Status.ACTIVE);
        if (activeCards.size() != INITIAL_CARD_COUNT) {
            throw new IllegalArgumentException("다른 추천 보기를 누른 뒤 새 카드 묶음에서 선택할 수 있습니다.");
        }
    }

    private Set<String> collectExcludedCategories(Collection<RecommendationVO> activeCards,
                                                  RecommendationVO lastRevealed) {
        Set<String> excluded = new LinkedHashSet<>();
        activeCards.stream()
                .map(RecommendationVO::getIabMainCategory)
                .forEach(excluded::add);
        excluded.add(lastRevealed.getIabMainCategory());
        return excluded;
    }

    private int findEmptyCardIndex(List<RecommendationVO> activeCards) {
        Set<Integer> occupied = new LinkedHashSet<>();
        activeCards.stream().map(RecommendationVO::getCardIndex).forEach(occupied::add);
        for (int i = 1; i <= INITIAL_CARD_COUNT; i++) {
            if (!occupied.contains(i)) {
                return i;
            }
        }
        return INITIAL_CARD_COUNT;
    }

    private void validateDailyShuffleLimit(Long userId) {
        LocalDate today = LocalDate.now(SERVICE_ZONE);
        LocalDateTime startAt = today.atStartOfDay();
        LocalDateTime endAt = today.plusDays(1).atStartOfDay().minusNanos(1);
        long count = recommendationRepository.countByUser_IdAndGenerationTypeAndCreatedAtBetween(
                userId, RecommendationVO.GenerationType.SHUFFLE, startAt, endAt);
        if (count >= DAILY_SHUFFLE_LIMIT) {
            throw new RecommendationLimitExceededException("오늘의 다른 추천 보기 횟수를 모두 사용했습니다.");
        }
    }

    private RecommendationProfile parseProfile(String profileSnapshot) {
        try {
            return objectMapper.readValue(profileSnapshot, RecommendationProfile.class);
        } catch (JsonProcessingException e) {
            throw new RecommendationGenerationException("추천 프로필 스냅샷을 파싱할 수 없습니다.", e);
        }
    }

    private RecommendationRevealResponse toRevealResponse(RecommendationVO card) {
        RecommendationContent content = parseContent(card.getContentRef());
        return new RecommendationRevealResponse(
                card.getDraw() == null ? null : card.getDraw().getId(),
                card.getId(),
                card.getIabMainCategory(),
                card.getIabSubCategory(),
                card.getContentType(),
                content.title(),
                content.description(),
                content.searchKeyword(),
                card.getReason()
        );
    }

    private List<RecommendationCardResponse> toBackCards(List<RecommendationVO> cards) {
        return cards.stream()
                .map(card -> new RecommendationCardResponse(card.getId(), card.getCardIndex(), false))
                .toList();
    }

    private RecommendationContent parseContent(String contentRef) {
        try {
            return objectMapper.readValue(contentRef, RecommendationContent.class);
        } catch (JsonProcessingException e) {
            log.warn("추천 카드 content_ref 파싱 실패: {}", contentRef);
            return new RecommendationContent("오늘의 추천", "추천 내용을 불러오지 못했습니다.", null);
        }
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
                category + " 추천",
                "최근 기록된 취향과 일상 패턴에 맞춘 가벼운 추천입니다.",
                category + " 추천",
                "사용자의 누적 일기 분석 데이터를 바탕으로 추천했습니다."
        );
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

    private record RecommendationContent(String title, String description, String searchKeyword) {
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
