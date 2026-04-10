package net.coboogie.common.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 모든 API 엔드포인트에서 사용하는 공통 응답 래퍼.
 * <p>
 * 성공 시: {@code success=true}, {@code data}에 실제 응답값, {@code message=null}
 * 실패 시: {@code success=false}, {@code data=null}, {@code message}에 오류 설명
 *
 * @param <T> 응답 데이터 타입
 */
@Schema(description = "공통 API 응답 형식")
public record ApiResponse<T>(
        @Schema(description = "성공 여부") boolean success,
        @Schema(description = "응답 데이터") T data,
        @Schema(description = "메시지 (에러 시 사용)") String message
) {
    /** 데이터가 있는 성공 응답을 생성한다. */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /** 데이터 없는 성공 응답을 생성한다 (204-like). */
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    /** 오류 메시지를 담은 실패 응답을 생성한다. */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
