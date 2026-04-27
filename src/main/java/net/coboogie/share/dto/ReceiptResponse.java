package net.coboogie.share.dto;

import java.util.Map;

/**
 * 영수증 공유 콘텐츠 응답 DTO.
 *
 * @param orderNumber         주문번호 (형식: FL-YYYYMMDD-NNN)
 * @param diaryCount          해당 월 일기 작성 개수
 * @param totalChars          해당 월 총 작성 글자 수
 * @param emotionDistribution 감정 분포 (감정명 → 비율 0~100)
 * @param personaTitle        최근 페르소나 제목 (없으면 null)
 */
public record ReceiptResponse(
        String orderNumber,
        int diaryCount,
        int totalChars,
        Map<String, Integer> emotionDistribution,
        String personaTitle
) {}
