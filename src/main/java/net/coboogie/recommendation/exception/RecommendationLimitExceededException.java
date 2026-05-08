package net.coboogie.recommendation.exception;

/**
 * 추천 shuffle 일일 제한을 초과했을 때 발생하는 예외.
 */
public class RecommendationLimitExceededException extends RuntimeException {

    /**
     * 추천 제한 초과 예외를 생성한다.
     *
     * @param message 예외 메시지
     */
    public RecommendationLimitExceededException(String message) {
        super(message);
    }
}
