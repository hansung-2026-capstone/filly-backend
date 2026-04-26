package net.coboogie.user.exception;

/**
 * 존재하지 않는 사용자 ID로 접근할 때 던지는 예외.
 * <p>
 * JWT는 유효하지만 해당 userId가 DB에 없는 경우(예: 계정 삭제 후 만료 전 토큰 사용)에 발생하며,
 * {@code GlobalExceptionHandler}가 이를 HTTP 401로 변환하여 클라이언트가 재로그인하도록 유도한다.
 */
public class UserNotFoundException extends RuntimeException {

    /**
     * 주어진 userId에 해당하는 사용자를 찾지 못했을 때 생성한다.
     *
     * @param userId 존재하지 않는 사용자 ID
     */
    public UserNotFoundException(Long userId) {
        super("사용자를 찾을 수 없습니다: " + userId);
    }
}
