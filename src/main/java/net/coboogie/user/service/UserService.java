package net.coboogie.user.service;

import lombok.RequiredArgsConstructor;
import net.coboogie.diary.service.GcsStorageService;
import net.coboogie.user.dto.UserResponse;
import net.coboogie.user.exception.UserNotFoundException;
import net.coboogie.user.repository.UserRepository;
import net.coboogie.vo.UserVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * 사용자 도메인 비즈니스 로직 서비스.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private static final String DEFAULT_PREFERENCE = "none";
    private static final Set<String> ALLOWED_GENDERS = Set.of("male", "female", DEFAULT_PREFERENCE);
    private static final Set<String> ALLOWED_AGE_GROUPS = Set.of(
            "10대", "20대", "30대", "40대", "50대", "60대", "70대 이상", DEFAULT_PREFERENCE);
    private static final Set<String> ALLOWED_AI_DRAFT_TONES = Set.of(
            "calm", "warm", "lively", "literary", "reflective", DEFAULT_PREFERENCE);

    private final UserRepository userRepository;
    private final GcsStorageService gcsStorageService;

    /**
     * 사용자 정보를 조회하여 반환한다.
     *
     * @param userId JWT 인증 사용자 ID
     * @return 사용자 정보 응답 DTO
     * @throws UserNotFoundException 사용자가 존재하지 않는 경우
     */
    @Transactional(readOnly = true)
    public UserResponse getMe(Long userId) {
        UserVO user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        String currentAvatarUrl = toSignedUrl(user.getCurrentAvatarUrl());
        return UserResponse.from(user, currentAvatarUrl);
    }

    private String toSignedUrl(String blobName) {
        if (blobName == null || blobName.isBlank()) {
            return blobName;
        }
        return gcsStorageService.generateSignedUrl(blobName);
    }

    /**
     * 사용자의 배경화면 테마를 수정한다.
     *
     * @param userId          JWT 인증 사용자 ID
     * @param backgroundTheme 변경할 배경 테마 식별자
     * @throws NoSuchElementException   사용자가 존재하지 않는 경우
     * @throws IllegalArgumentException 테마 값이 null이거나 공백인 경우
     */
    @Transactional
    public void updateBackgroundTheme(Long userId, String backgroundTheme) {
        if (backgroundTheme == null || backgroundTheme.isBlank()) {
            throw new IllegalArgumentException("배경 테마는 비워 둘 수 없습니다.");
        }

        UserVO user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        user.setBackgroundTheme(backgroundTheme);
    }

    /**
     * 사용자의 닉네임을 수정한다.
     *
     * @param userId   JWT 인증 사용자 ID
     * @param nickname 변경할 닉네임
     * @throws NoSuchElementException   사용자가 존재하지 않는 경우
     * @throws IllegalArgumentException 닉네임이 null이거나 공백인 경우
     */
    @Transactional
    public void updateNickname(Long userId, String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("닉네임은 비워 둘 수 없습니다.");
        }

        UserVO user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        user.setNickname(nickname);
    }

    /**
     * 사용자의 개인화 설정을 수정한다.
     *
     * @param userId      JWT 인증 사용자 ID
     * @param gender      성별
     * @param ageGroup    나이대
     * @param aiDraftTone AI 초안 어투
     * @throws UserNotFoundException    사용자가 존재하지 않는 경우
     * @throws IllegalArgumentException 허용되지 않은 설정 값인 경우
     */
    @Transactional
    public void updatePreferences(Long userId, String gender, String ageGroup, String aiDraftTone) {
        String normalizedGender = normalizePreference(gender);
        String normalizedAgeGroup = normalizePreference(ageGroup);
        String normalizedAiDraftTone = normalizePreference(aiDraftTone);

        validateAllowed("성별", normalizedGender, ALLOWED_GENDERS);
        validateAllowed("나이대", normalizedAgeGroup, ALLOWED_AGE_GROUPS);
        validateAllowed("AI 초안 어투", normalizedAiDraftTone, ALLOWED_AI_DRAFT_TONES);

        UserVO user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        user.setGender(normalizedGender);
        user.setAgeGroup(normalizedAgeGroup);
        user.setAiDraftTone(normalizedAiDraftTone);
    }

    private String normalizePreference(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_PREFERENCE;
        }
        return value.trim();
    }

    private void validateAllowed(String fieldName, String value, Set<String> allowedValues) {
        if (!allowedValues.contains(value)) {
            throw new IllegalArgumentException(fieldName + " 값이 허용되지 않습니다: " + value);
        }
    }
}
