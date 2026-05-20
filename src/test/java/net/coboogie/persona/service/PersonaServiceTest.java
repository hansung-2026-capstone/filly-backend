package net.coboogie.persona.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.coboogie.avatar.service.AvatarService;
import net.coboogie.diary.repository.AiEmotionAnalysisRepository;
import net.coboogie.diary.repository.DiaryEntryRepository;
import net.coboogie.persona.repository.PersonaSnapshotRepository;
import net.coboogie.user.repository.UserRepository;
import net.coboogie.vo.PersonaSnapshotVO;
import net.coboogie.vo.UserVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonaServiceTest {

    @Mock private PersonaSnapshotRepository personaSnapshotRepository;
    @Mock private DiaryEntryRepository diaryEntryRepository;
    @Mock private AiEmotionAnalysisRepository aiEmotionAnalysisRepository;
    @Mock private UserRepository userRepository;
    @Mock private ChatClient personaChatClient;
    @Mock private ObjectMapper objectMapper;
    @Mock private AvatarService avatarService;

    private PersonaService sut;
    private final Long userId = 1L;

    @BeforeEach
    void setUp() {
        sut = new PersonaService(
                personaSnapshotRepository,
                diaryEntryRepository,
                aiEmotionAnalysisRepository,
                userRepository,
                personaChatClient,
                objectMapper,
                avatarService
        );
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    @DisplayName("최근 30일 일기가 5개 미만인 경우 예외를 발생시킨다")
    void givenDiaryCountLessThanRequired_whenGenerate_thenThrowIllegalStateException() {
        // given
        given(diaryEntryRepository.countByUser_IdAndWrittenAtBetween(eq(userId), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(4);

        // when & then
        assertThatThrownBy(() -> sut.generate(userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("최근 30일 일기가 5개 미만입니다.");

        verify(personaSnapshotRepository, never()).findLatestByUserId(any());
    }

    @Test
    @DisplayName("마지막 페르소나 생성 이후 7일이 경과하지 않은 경우 예외를 발생시킨다")
    void givenIntervalNotPassed_whenGenerate_thenThrowIllegalStateException() {
        // given
        given(diaryEntryRepository.countByUser_IdAndWrittenAtBetween(eq(userId), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(5);

        PersonaSnapshotVO latest = PersonaSnapshotVO.builder()
                .id(100L)
                .generatedAt(LocalDateTime.now().minusDays(6)) // 6일 전에 생성됨
                .build();
        given(personaSnapshotRepository.findLatestByUserId(userId)).willReturn(Optional.of(latest));

        // when & then
        assertThatThrownBy(() -> sut.generate(userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("페르소나는 7일에 한 번 생성할 수 있습니다.");

        verify(diaryEntryRepository, never()).existsByUser_IdAndCreatedAtAfter(any(), any());
    }

    @Test
    @DisplayName("마지막 생성 이후로 7일이 경과했으나 추가로 생성된 일기가 없는 경우 예외를 발생시킨다")
    void givenNoNewDiaryAfterLatestPersona_whenGenerate_thenThrowIllegalStateException() {
        // given
        given(diaryEntryRepository.countByUser_IdAndWrittenAtBetween(eq(userId), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(6);

        LocalDateTime generatedAt = LocalDateTime.now().minusDays(8); // 8일 전에 생성됨
        PersonaSnapshotVO latest = PersonaSnapshotVO.builder()
                .id(100L)
                .generatedAt(generatedAt)
                .build();
        given(personaSnapshotRepository.findLatestByUserId(userId)).willReturn(Optional.of(latest));
        
        // 마지막 생성시점 이후 새로 등록된 일기가 없음
        given(diaryEntryRepository.existsByUser_IdAndCreatedAtAfter(userId, generatedAt)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> sut.generate(userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("마지막 페르소나 생성 이후 추가된 일기가 없습니다.");

        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("최초 생성이고 30일 내 일기가 5개 이상이면 정상 작동 테스트로 이어진다")
    @SuppressWarnings("unchecked")
    void givenNoLatestPersona_whenGenerate_thenProceedsNormalGeneration() throws IOException {
        // given
        given(diaryEntryRepository.countByUser_IdAndWrittenAtBetween(eq(userId), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(5);
        given(personaSnapshotRepository.findLatestByUserId(userId)).willReturn(Optional.empty());
        
        // 이후 정상 프로세스 Mocking (사용자 정보 로드)
        UserVO mockUser = UserVO.builder().id(userId).build();
        given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));
        given(aiEmotionAnalysisRepository.findByUserIdAndDateRange(eq(userId), any(), any()))
                .willReturn(Collections.emptyList());

        // AI ChatClient 호출 Mocking
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        given(personaChatClient.prompt()).willReturn(requestSpec);
        given(requestSpec.user(any(String.class))).willReturn(requestSpec);
        given(requestSpec.call()).willReturn(responseSpec);
        given(responseSpec.content()).willReturn("{\"title\":\"새로운 페르소나\",\"summary\":\"내용\"}");

        // ObjectMapper Mocking
        Map<String, String> parsedJson = Map.of("title", "새로운 페르소나", "summary", "내용");
        given(objectMapper.readValue(anyString(), eq(Map.class))).willReturn(parsedJson);

        // 저장 Mocking
        PersonaSnapshotVO saved = PersonaSnapshotVO.builder().id(101L).build();
        given(personaSnapshotRepository.save(any())).willReturn(saved);

        // when
        sut.generate(userId);

        // then
        verify(diaryEntryRepository, never()).existsByUser_IdAndCreatedAtAfter(any(), any());
        verify(personaSnapshotRepository).save(any(PersonaSnapshotVO.class));
    }
}
