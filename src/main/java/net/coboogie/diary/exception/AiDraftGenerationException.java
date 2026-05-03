package net.coboogie.diary.exception;

/**
 * AI 초안 생성 또는 응답 파싱에 실패했을 때 발생하는 예외.
 */
public class AiDraftGenerationException extends RuntimeException {

    public AiDraftGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
