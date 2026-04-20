package net.coboogie.diary.repository;

import net.coboogie.vo.AiEmotionAnalysisVO;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code ai_diary_analysis} 테이블에 대한 JPA Repository.
 */
public interface AiEmotionAnalysisRepository extends JpaRepository<AiEmotionAnalysisVO, Long> {
}
