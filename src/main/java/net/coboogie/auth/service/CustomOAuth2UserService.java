package net.coboogie.auth.service;

import net.coboogie.auth.dto.CustomOAuth2User;
import net.coboogie.vo.UserVO;
import net.coboogie.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;
    private final UserRepository userRepository;

    @Autowired
    public CustomOAuth2UserService(UserRepository userRepository) {
        this(new DefaultOAuth2UserService(), userRepository);
    }

    CustomOAuth2UserService(OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate, UserRepository userRepository) {
        this.delegate = delegate;
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        String provider = userRequest.getClientRegistration().getRegistrationId();
        String oauthId = extractOauthId(provider, oAuth2User);
        String nickname = extractNickname(provider, oAuth2User);

        UserVO user = userRepository.findByOauthProviderAndOauthId(provider, oauthId)
                .orElseGet(() -> userRepository.save(
                        UserVO.builder()
                                .oauthProvider(provider)
                                .oauthId(oauthId)
                                .nickname(nickname)
                                .build()
                ));

        return new CustomOAuth2User(oAuth2User, user.getId());
    }

    private String extractOauthId(String provider, OAuth2User oAuth2User) {
        return switch (provider) {
            case "google" -> oAuth2User.getAttribute("sub");
            case "kakao" -> {
                Object id = oAuth2User.getAttribute("id");
                yield id.toString();
            }
            case "naver" -> {
                Map<String, Object> response = oAuth2User.getAttribute("response");
                yield (String) response.get("id");
            }
            default -> throw new OAuth2AuthenticationException("Unsupported provider: " + provider);
        };
    }

    @SuppressWarnings("unchecked")
    private String extractNickname(String provider, OAuth2User oAuth2User) {
        return switch (provider) {
            case "google" -> oAuth2User.getAttribute("name");
            case "kakao" -> {
                Map<String, Object> kakaoAccount = oAuth2User.getAttribute("kakao_account");
                Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
                yield (String) profile.get("nickname");
            }
            case "naver" -> {
                Map<String, Object> response = oAuth2User.getAttribute("response");
                yield (String) response.get("nickname");
            }
            default -> throw new OAuth2AuthenticationException("Unsupported provider: " + provider);
        };
    }
}
