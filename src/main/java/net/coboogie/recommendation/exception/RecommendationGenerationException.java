package net.coboogie.recommendation.exception;

/**
 * 추천 생성 AI 응답을 만들거나 파싱하지 못했을 때 발생하는 예외.
 */
public class RecommendationGenerationException extends RuntimeException {

    /**
     * 추천 생성 예외를 생성한다.
     *
     * @param message 예외 메시지
     * @param cause   원인 예외
     */
    public RecommendationGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
