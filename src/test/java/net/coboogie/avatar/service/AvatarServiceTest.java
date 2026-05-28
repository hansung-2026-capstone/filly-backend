package net.coboogie.avatar.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.coboogie.avatar.dto.AvatarResponse;
import net.coboogie.avatar.repository.AvatarHistoryRepository;
import net.coboogie.diary.service.GcsStorageService;
import net.coboogie.persona.repository.PersonaSnapshotRepository;
import net.coboogie.user.repository.UserRepository;
import net.coboogie.vo.AvatarHistoryVO;
import net.coboogie.vo.PersonaSnapshotVO;
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

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AvatarServiceTest {

    @Mock private AvatarHistoryRepository avatarHistoryRepository;
    @Mock private PersonaSnapshotRepository personaSnapshotRepository;
    @Mock private UserRepository userRepository;
    @Mock private VertexAiImagenService vertexAiImagenService;
    @Mock private GcsStorageService gcsStorageService;
    @Mock private ChatClient avatarChatClient;
    @Mock private ObjectMapper objectMapper;

    private AvatarService sut;

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

        sut = new AvatarService(
                avatarHistoryRepository,
                personaSnapshotRepository,
                userRepository,
                vertexAiImagenService,
                gcsStorageService,
                avatarChatClient,
                objectMapper,
                transactionManager
        );
    }

    @Test
    @DisplayName("아바타 생성 시 외부 호출 결과를 저장하고 서명 URL을 반환한다")
    @SuppressWarnings("unchecked")
    void givenPersona_whenGenerateAvatar_thenSaveHistoryAndReturnSignedUrl() throws IOException, InterruptedException {
        // given
        Long userId = 1L;
        Long personaId = 10L;
        byte[] imageBytes = new byte[] {1, 2, 3};
        String blobPath = "avatars/avatar.png";
        String signedUrl = "https://signed.example/avatar.png";
        UserVO user = UserVO.builder().id(userId).build();
        PersonaSnapshotVO persona = PersonaSnapshotVO.builder()
                .id(personaId)
                .user(user)
                .title("느긋한 관찰자")
                .summary("차분하게 일상을 기록한다.")
                .build();
        AvatarHistoryVO saved = AvatarHistoryVO.builder()
                .id(100L)
                .user(user)
                .personaSnapshot(persona)
                .gcsUrl(blobPath)
                .status(AvatarHistoryVO.Status.COMPLETED)
                .build();

        given(personaSnapshotRepository.findLatestByUserId(userId)).willReturn(Optional.of(persona));
        given(personaSnapshotRepository.findById(personaId)).willReturn(Optional.of(persona));
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        mockAvatarParamResponse();
        given(vertexAiImagenService.generateImage(anyString())).willReturn(imageBytes);
        given(gcsStorageService.uploadBytes(imageBytes, "avatars", "avatar.png", "image/png")).willReturn(blobPath);
        given(gcsStorageService.generateSignedUrl(blobPath)).willReturn(signedUrl);
        given(avatarHistoryRepository.save(any(AvatarHistoryVO.class))).willReturn(saved);

        // when
        AvatarResponse response = sut.generateAvatar(userId);

        // then
        assertThat(response.avatarHistoryId()).isEqualTo(100L);
        assertThat(response.avatarUrl()).isEqualTo(signedUrl);
        assertThat(user.getCurrentAvatarUrl()).isEqualTo(blobPath);
        verify(avatarHistoryRepository).save(any(AvatarHistoryVO.class));
    }

    private void mockAvatarParamResponse() throws IOException {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        given(avatarChatClient.prompt()).willReturn(requestSpec);
        given(requestSpec.user(any(String.class))).willReturn(requestSpec);
        given(requestSpec.call()).willReturn(responseSpec);
        given(responseSpec.content()).willReturn("{\"animal\":\"cat\",\"backgroundColor\":\"#E8E8E8\",\"pose\":\"sitting\"}");
        given(objectMapper.readValue(anyString(), eq(Map.class)))
                .willReturn(Map.of("animal", "cat", "backgroundColor", "#E8E8E8", "pose", "sitting"));
    }
}
