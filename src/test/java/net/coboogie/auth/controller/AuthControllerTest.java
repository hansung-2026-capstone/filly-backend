package net.coboogie.auth.controller;

import net.coboogie.auth.util.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthController authController;

    private static final String VALID_REFRESH_TOKEN = "valid.refresh.token";
    private static final String INVALID_REFRESH_TOKEN = "invalid.refresh.token";
    private static final Long USER_ID = 1L;

    @Test
    @DisplayName("유효한 refreshToken으로 새 토큰 쌍 발급 성공")
    void givenValidRefreshToken_whenRefresh_thenReturnNewTokens() {
        // given
        given(jwtTokenProvider.validateToken(VALID_REFRESH_TOKEN)).willReturn(true);
        given(jwtTokenProvider.getUserIdFromToken(VALID_REFRESH_TOKEN)).willReturn(USER_ID);
        given(jwtTokenProvider.generateAccessToken(USER_ID)).willReturn("new.access.token");
        given(jwtTokenProvider.generateRefreshToken(USER_ID)).willReturn("new.refresh.token");

        // when
        ResponseEntity<?> response = authController.refresh(
                new AuthController.TokenRefreshRequest(VALID_REFRESH_TOKEN)
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = response.getBody();
        assertThat(body).isNotNull();
        var data = ((net.coboogie.common.response.ApiResponse<?>) body).data();
        assertThat(data).isNotNull();
        var tokenResponse = (net.coboogie.auth.dto.TokenResponse) data;
        assertThat(tokenResponse.getAccessToken()).isEqualTo("new.access.token");
        assertThat(tokenResponse.getRefreshToken()).isEqualTo("new.refresh.token");
    }

    @Test
    @DisplayName("만료된 refreshToken으로 갱신 시도 시 401 반환")
    void givenInvalidRefreshToken_whenRefresh_thenReturn401() {
        // given
        given(jwtTokenProvider.validateToken(INVALID_REFRESH_TOKEN)).willReturn(false);

        // when
        ResponseEntity<?> response = authController.refresh(
                new AuthController.TokenRefreshRequest(INVALID_REFRESH_TOKEN)
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var body = (net.coboogie.common.response.ApiResponse<?>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isFalse();
        assertThat(body.message()).isEqualTo("refreshToken이 만료되었거나 유효하지 않습니다.");
    }
}
