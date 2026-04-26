package net.coboogie.common.exception;

import net.coboogie.common.response.ApiResponse;
import net.coboogie.user.exception.UserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

/**
 * 전역 예외 처리 핸들러.
 * <p>
 * 컨트롤러에서 잡히지 않은 예외를 일관된 {@link ApiResponse} 형식으로 변환한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 삭제된 사용자 등 DB에 존재하지 않는 userId로 접근 시 401을 반환한다.
     * 클라이언트는 이 응답을 받으면 토큰을 삭제하고 로그인 화면으로 이동해야 한다.
     *
     * @param ex 발생한 예외
     * @return 401 Unauthorized
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * 일기 등 일반 리소스를 찾지 못한 경우 404를 반환한다.
     *
     * @param ex 발생한 예외
     * @return 404 Not Found
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoSuchElement(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * 잘못된 입력값(null 닉네임, 빈 테마 등)에 대해 400을 반환한다.
     *
     * @param ex 발생한 예외
     * @return 400 Bad Request
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }
}
