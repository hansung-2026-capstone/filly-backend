package net.coboogie.recommendation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.coboogie.recommendation.dto.RecommendationDrawResponse;
import net.coboogie.recommendation.repository.RecommendationDrawRepository;
import net.coboogie.recommendation.repository.RecommendationRepository;
import net.coboogie.user.repository.UserRepository;
import net.coboogie.vo.RecommendationDrawVO;
import net.coboogie.vo.RecommendationVO;
import net.coboogie.vo.UserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RecommendationDrawRepository recommendationDrawRepository;
    @Mock private RecommendationRepository recommendationRepository;
    @Mock private RecommendationProfileBuilder recommendationProfileBuilder;
    @Mock private RecommendationCategorySelector recommendationCategorySelector;
    @Mock private RecommendationGenerationAsyncService recommendationGenerationAsyncService;
    @Mock private ChatClient recommendationChatClient;
    @Mock private ObjectMapper objectMapper;
    @Mock private ObjectMapper aiObjectMapper;

    private RecommendationService sut;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager transactionManager = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
        sut = new RecommendationService(
                userRepository,
                recommendationDrawRepository,
                recommendationRepository,
                recommendationProfileBuilder,
                recommendationCategorySelector,
                recommendationGenerationAsyncService,
                recommendationChatClient,
                objectMapper,
                aiObjectMapper,
                transactionManager
        );
    }

    @Test
    @DisplayName("기존 ACTIVE 추천 카드가 있으면 즉시 반환하고 비동기 생성을 호출하지 않는다")
    void givenReusableActiveDraw_whenDraw_thenReturnCardsWithoutAsyncGeneration() throws Exception {
        Long userId = 1L;
        RecommendationProfile profile = profile();
        RecommendationDrawVO draw = draw(10L, RecommendationDrawVO.Status.ACTIVE);
        RecommendationVO card = card(draw, 100L, 1);

        given(userRepository.findById(userId)).willReturn(Optional.of(user(userId)));
        given(recommendationDrawRepository.findTopByUserIdAndStatusInOrderByUpdatedAtDesc(
                eq(userId), any())).willReturn(Optional.of(draw));
        given(objectMapper.readValue(any(String.class), eq(RecommendationProfile.class))).willReturn(profile);
        given(recommendationRepository.findByDraw_IdAndStatusInOrderByCardIndexAsc(
                draw.getId(), List.of(RecommendationVO.Status.ACTIVE, RecommendationVO.Status.REVEALED)))
                .willReturn(List.of(card));

        RecommendationDrawResponse response = sut.draw(userId);

        assertThat(response.status()).isEqualTo(RecommendationDrawVO.Status.ACTIVE);
        assertThat(response.cards()).hasSize(1);
        verify(recommendationGenerationAsyncService, never()).generateInitialCards(any(), any(), any());
    }

    @Test
    @DisplayName("기존 PENDING 추천 세션이 있으면 새 세션을 만들지 않고 PENDING을 반환한다")
    void givenPendingDraw_whenDraw_thenReturnPendingWithoutAsyncGeneration() throws Exception {
        Long userId = 1L;
        RecommendationProfile profile = profile();
        RecommendationDrawVO draw = draw(10L, RecommendationDrawVO.Status.PENDING);

        given(userRepository.findById(userId)).willReturn(Optional.of(user(userId)));
        given(recommendationDrawRepository.findTopByUserIdAndStatusInOrderByUpdatedAtDesc(
                eq(userId), any())).willReturn(Optional.of(draw));
        given(objectMapper.readValue(any(String.class), eq(RecommendationProfile.class))).willReturn(profile);

        RecommendationDrawResponse response = sut.draw(userId);

        assertThat(response.status()).isEqualTo(RecommendationDrawVO.Status.PENDING);
        assertThat(response.cards()).isEmpty();
        verify(recommendationDrawRepository, never()).save(any());
        verify(recommendationGenerationAsyncService, never()).generateInitialCards(any(), any(), any());
    }

    @Test
    @DisplayName("재사용 가능한 추천 세션이 없으면 PENDING 세션을 만들고 비동기 생성을 시작한다")
    void givenNoReusableDraw_whenDraw_thenCreatePendingAndStartAsyncGeneration() throws Exception {
        Long userId = 1L;
        RecommendationProfile profile = profile();
        UserVO user = user(userId);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(recommendationDrawRepository.findTopByUserIdAndStatusInOrderByUpdatedAtDesc(
                eq(userId), any())).willReturn(Optional.empty());
        given(recommendationProfileBuilder.build(userId)).willReturn(profile);
        given(objectMapper.writeValueAsString(any(RecommendationProfile.class)))
                .willReturn("{\"recommendationPromptVersion\":4}");
        given(recommendationDrawRepository.save(any(RecommendationDrawVO.class))).willAnswer(invocation -> {
            RecommendationDrawVO saved = invocation.getArgument(0);
            saved.setId(20L);
            return saved;
        });

        RecommendationDrawResponse response = sut.draw(userId);

        assertThat(response.drawId()).isEqualTo(20L);
        assertThat(response.status()).isEqualTo(RecommendationDrawVO.Status.PENDING);
        assertThat(response.cards()).isEmpty();
        verify(recommendationGenerationAsyncService).generateInitialCards(20L, userId, profile);
    }

    @Test
    @DisplayName("추천 세션 상태 조회 시 ACTIVE이면 카드 목록을 함께 반환한다")
    void givenActiveDraw_whenGetDraw_thenReturnCards() {
        Long userId = 1L;
        Long drawId = 10L;
        RecommendationDrawVO draw = draw(drawId, RecommendationDrawVO.Status.ACTIVE);
        RecommendationVO card = card(draw, 100L, 1);

        given(recommendationDrawRepository.findByIdAndUserId(drawId, userId)).willReturn(Optional.of(draw));
        given(recommendationRepository.findByDraw_IdAndStatusInOrderByCardIndexAsc(
                drawId, List.of(RecommendationVO.Status.ACTIVE, RecommendationVO.Status.REVEALED)))
                .willReturn(List.of(card));

        RecommendationDrawResponse response = sut.getDraw(userId, drawId);

        assertThat(response.status()).isEqualTo(RecommendationDrawVO.Status.ACTIVE);
        assertThat(response.cards()).hasSize(1);
    }

    private UserVO user(Long userId) {
        return UserVO.builder()
                .id(userId)
                .oauthProvider("google")
                .oauthId("oauth-" + userId)
                .build();
    }

    private RecommendationDrawVO draw(Long drawId, RecommendationDrawVO.Status status) {
        return RecommendationDrawVO.builder()
                .id(drawId)
                .user(user(1L))
                .currentRound(1)
                .sourcePeriod("2026-02-23~2026-05-24")
                .profileSnapshot("{\"recommendationPromptVersion\":4}")
                .status(status)
                .build();
    }

    private RecommendationVO card(RecommendationDrawVO draw, Long cardId, int cardIndex) {
        return RecommendationVO.builder()
                .id(cardId)
                .draw(draw)
                .user(draw.getUser())
                .roundNo(1)
                .iabMainCategory("문화/예술")
                .iabSubCategory("음악")
                .contentType("MUSIC")
                .contentRef("{\"title\":\"추천\",\"description\":\"설명\",\"searchKeyword\":\"추천\"}")
                .reason("추천 이유")
                .cardIndex(cardIndex)
                .revealed(false)
                .status(RecommendationVO.Status.ACTIVE)
                .generationType(RecommendationVO.GenerationType.INITIAL)
                .build();
    }

    private RecommendationProfile profile() {
        return new RecommendationProfile(
                RecommendationProfile.CURRENT_RECOMMENDATION_PROMPT_VERSION,
                "2026-02-23~2026-05-24",
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                0,
                null,
                null
        );
    }
}
