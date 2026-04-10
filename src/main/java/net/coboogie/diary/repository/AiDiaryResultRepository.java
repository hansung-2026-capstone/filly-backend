package net.coboogie.diary.repository;

import net.coboogie.vo.AiDiaryResultVO;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code ai_diary_results} 테이블에 대한 JPA Repository.
 * AI가 생성한 일기 텍스트를 일기 항목과 연결하여 저장·조회한다.
 */
public interface AiDiaryResultRepository extends JpaRepository<AiDiaryResultVO, Long> {
}
