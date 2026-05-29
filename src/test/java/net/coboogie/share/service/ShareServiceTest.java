package net.coboogie.share.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.coboogie.diary.repository.AiEmotionAnalysisRepository;
import net.coboogie.diary.repository.DiaryEntryRepository;
import net.coboogie.diary.service.GcsStorageService;
import net.coboogie.persona.repository.PersonaSnapshotRepository;
import net.coboogie.share.dto.IdCardResponse;
import net.coboogie.user.repository.UserRepository;
import net.coboogie.vo.UserVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class ShareServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DiaryEntryRepository diaryEntryRepository;

    @Mock
    private AiEmotionAnalysisRepository aiEmotionAnalysisRepository;

    @Mock
    private PersonaSnapshotRepository personaSnapshotRepository;

    @Mock
    private GcsStorageService gcsStorageService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ShareService sut;

    @Test
    @DisplayName("ID 카드 조회 시 아바타는 data URL 대신 공개 URL로 반환한다")
    void givenAvatarBlobPath_whenGetIdCard_thenReturnPublicAvatarUrl() {
        // given
        Long userId = 1L;
        String blobPath = "avatars/avatar.png";
        String publicUrl = "https://storage.googleapis.com/filly-public-media-bucket/avatars/avatar.png";
        UserVO user = UserVO.builder()
                .id(userId)
                .oauthProvider("google")
                .oauthId("oauth-id")
                .nickname("tester")
                .currentAvatarUrl(blobPath)
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(aiEmotionAnalysisRepository.findByDiary_User_Id(userId)).willReturn(Collections.emptyList());
        given(gcsStorageService.generatePublicAvatarUrl(blobPath)).willReturn(publicUrl);

        // when
        IdCardResponse response = sut.getIdCard(userId);

        // then
        assertThat(response.avatarUrl()).isEqualTo(publicUrl);
        assertThat(response.nickname()).isEqualTo("tester");
        assertThat(response.keywords()).isEmpty();
        verify(gcsStorageService).generatePublicAvatarUrl(blobPath);
        verifyNoMoreInteractions(gcsStorageService);
    }
}
