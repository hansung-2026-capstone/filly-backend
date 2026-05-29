package net.coboogie.user.service;

import net.coboogie.diary.service.GcsStorageService;
import net.coboogie.user.dto.UserResponse;
import net.coboogie.user.repository.UserRepository;
import net.coboogie.vo.UserVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private GcsStorageService gcsStorageService;

    @InjectMocks
    private UserService sut;

    @Test
    @DisplayName("내 정보 조회 시 아바타는 서명 URL 대신 공개 URL로 반환한다")
    void givenAvatarBlobPath_whenGetMe_thenReturnPublicAvatarUrl() {
        // given
        String blobPath = "avatars/avatar.png";
        String publicUrl = "https://storage.googleapis.com/filly-media-bucket/avatars/avatar.png";
        UserVO user = UserVO.builder()
                .id(1L)
                .oauthProvider("google")
                .oauthId("oauth-id")
                .nickname("tester")
                .currentAvatarUrl(blobPath)
                .build();

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(gcsStorageService.generatePublicAvatarUrl(blobPath)).willReturn(publicUrl);

        // when
        UserResponse response = sut.getMe(1L);

        // then
        assertThat(response.currentAvatarUrl()).isEqualTo(publicUrl);
        verify(gcsStorageService).generatePublicAvatarUrl(blobPath);
        verifyNoMoreInteractions(gcsStorageService);
    }
}
