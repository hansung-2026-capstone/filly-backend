package net.coboogie.diary.repository;

import net.coboogie.vo.AiEmotionAnalysisVO;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code ai_emotion_analysis} 테이블에 대한 JPA Repository.
 * 일기에 대한 AI 감정 분석 결과(감정 유형, 점수, 기분 지수, 키워드)를 저장·조회한다.
 */
public interface AiEmotionAnalysisRepository extends JpaRepository<AiEmotionAnalysisVO, Long> {
}
