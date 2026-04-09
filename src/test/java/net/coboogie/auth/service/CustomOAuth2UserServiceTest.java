package net.coboogie.auth.service;

import net.coboogie.auth.dto.CustomOAuth2User;
import net.coboogie.fillybackend.vo.UserVO;
import net.coboogie.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OAuth2User oAuth2User;

    private CustomOAuth2UserService sut;

    @BeforeEach
    void setUp() {
        sut = new CustomOAuth2UserService(delegate, userRepository);
    }

    @Test
    @DisplayName("Google 신규 사용자 회원가입 성공")
    void givenNewGoogleUser_whenLoadUser_thenSaveAndReturnCustomOAuth2User() {
        // given
        OAuth2UserRequest userRequest = createUserRequest("google");
        given(delegate.loadUser(userRequest)).willReturn(oAuth2User);
        given(oAuth2User.getAttribute("sub")).willReturn("google-oauth-id-123");
        given(oAuth2User.getAttribute("name")).willReturn("테스트유저");
        given(userRepository.findByOauthProviderAndOauthId("google", "google-oauth-id-123"))
                .willReturn(Optional.empty());

        UserVO savedUser = UserVO.builder().id(1L).oauthProvider("google")
                .oauthId("google-oauth-id-123").nickname("테스트유저").build();
        given(userRepository.save(any(UserVO.class))).willReturn(savedUser);

        // when
        OAuth2User result = sut.loadUser(userRequest);

        // then
        assertThat(result).isInstanceOf(CustomOAuth2User.class);
        assertThat(((CustomOAuth2User) result).getUserId()).isEqualTo(1L);
        verify(userRepository).save(any(UserVO.class));
    }

    @Test
    @DisplayName("Google 기존 사용자 로그인 성공 - DB 저장 없이 반환")
    void givenExistingGoogleUser_whenLoadUser_thenReturnWithoutSave() {
        // given
        OAuth2UserRequest userRequest = createUserRequest("google");
        given(delegate.loadUser(userRequest)).willReturn(oAuth2User);
        given(oAuth2User.getAttribute("sub")).willReturn("google-oauth-id-123");
        given(oAuth2User.getAttribute("name")).willReturn("테스트유저");

        UserVO existingUser = UserVO.builder().id(2L).oauthProvider("google")
                .oauthId("google-oauth-id-123").nickname("테스트유저").build();
        given(userRepository.findByOauthProviderAndOauthId("google", "google-oauth-id-123"))
                .willReturn(Optional.of(existingUser));

        // when
        OAuth2User result = sut.loadUser(userRequest);

        // then
        assertThat(result).isInstanceOf(CustomOAuth2User.class);
        assertThat(((CustomOAuth2User) result).getUserId()).isEqualTo(2L);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Kakao 신규 사용자 회원가입 성공")
    void givenNewKakaoUser_whenLoadUser_thenSaveAndReturnCustomOAuth2User() {
        // given
        OAuth2UserRequest userRequest = createUserRequest("kakao");
        given(delegate.loadUser(userRequest)).willReturn(oAuth2User);
        given(oAuth2User.getAttribute("id")).willReturn(98765432L);

        Map<String, Object> profile = new HashMap<>();
        profile.put("nickname", "카카오유저");
        Map<String, Object> kakaoAccount = new HashMap<>();
        kakaoAccount.put("profile", profile);
        given(oAuth2User.getAttribute("kakao_account")).willReturn(kakaoAccount);

        given(userRepository.findByOauthProviderAndOauthId("kakao", "98765432"))
                .willReturn(Optional.empty());

        UserVO savedUser = UserVO.builder().id(3L).oauthProvider("kakao")
                .oauthId("98765432").nickname("카카오유저").build();
        given(userRepository.save(any(UserVO.class))).willReturn(savedUser);

        // when
        OAuth2User result = sut.loadUser(userRequest);

        // then
        assertThat(result).isInstanceOf(CustomOAuth2User.class);
        assertThat(((CustomOAuth2User) result).getUserId()).isEqualTo(3L);
        verify(userRepository).save(any(UserVO.class));
    }

    @Test
    @DisplayName("Naver 신규 사용자 회원가입 성공")
    void givenNewNaverUser_whenLoadUser_thenSaveAndReturnCustomOAuth2User() {
        // given
        OAuth2UserRequest userRequest = createUserRequest("naver");
        given(delegate.loadUser(userRequest)).willReturn(oAuth2User);

        Map<String, Object> naverResponse = new HashMap<>();
        naverResponse.put("id", "naver-id-abc");
        naverResponse.put("nickname", "네이버유저");
        given(oAuth2User.getAttribute("response")).willReturn(naverResponse);

        given(userRepository.findByOauthProviderAndOauthId("naver", "naver-id-abc"))
                .willReturn(Optional.empty());

        UserVO savedUser = UserVO.builder().id(4L).oauthProvider("naver")
                .oauthId("naver-id-abc").nickname("네이버유저").build();
        given(userRepository.save(any(UserVO.class))).willReturn(savedUser);

        // when
        OAuth2User result = sut.loadUser(userRequest);

        // then
        assertThat(result).isInstanceOf(CustomOAuth2User.class);
        assertThat(((CustomOAuth2User) result).getUserId()).isEqualTo(4L);
        verify(userRepository).save(any(UserVO.class));
    }

    private OAuth2UserRequest createUserRequest(String provider) {
        ClientRegistration clientRegistration = ClientRegistration
                .withRegistrationId(provider)
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://example.com/oauth/authorize")
                .tokenUri("https://example.com/oauth/token")
                .userInfoUri("https://example.com/oauth/userinfo")
                .userNameAttributeName("id")
                .build();

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "test-token",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );

        return new OAuth2UserRequest(clientRegistration, accessToken);
    }
}
