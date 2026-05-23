package net.coboogie.diary.repository;

import net.coboogie.vo.AiDiaryResultVO;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code ai_diary_results} 테이블에 대한 JPA Repository.
 * AI가 생성한 일기 텍스트를 일기 항목과 연결하여 저장·조회한다.
 */
public interface AiDiaryResultRepository extends JpaRepository<AiDiaryResultVO, Long> {

    /**
     * 특정 일기의 AI 생성 텍스트 결과를 삭제한다.
     *
     * @param diaryId 일기 ID
     * @return 삭제된 결과 수
     */
    long deleteByDiaryId(Long diaryId);
}
