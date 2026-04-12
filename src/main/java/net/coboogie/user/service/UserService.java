package net.coboogie.user.service;

import lombok.RequiredArgsConstructor;
import net.coboogie.user.dto.UserResponse;
import net.coboogie.user.repository.UserRepository;
import net.coboogie.vo.UserVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * 사용자 도메인 비즈니스 로직 서비스.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * 사용자 정보를 조회하여 반환한다.
     *
     * @param userId JWT 인증 사용자 ID
     * @return 사용자 정보 응답 DTO
     * @throws NoSuchElementException 사용자가 존재하지 않는 경우
     */
    @Transactional(readOnly = true)
    public UserResponse getMe(Long userId) {
        UserVO user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다: " + userId));
        return UserResponse.from(user);
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
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다: " + userId));

        user.setNickname(nickname);
    }
}
