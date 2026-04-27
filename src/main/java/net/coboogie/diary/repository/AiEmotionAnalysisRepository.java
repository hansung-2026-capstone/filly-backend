package net.coboogie.diary.repository;

import net.coboogie.vo.AiEmotionAnalysisVO;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * {@code ai_diary_analysis} 테이블에 대한 JPA Repository.
 */
public interface AiEmotionAnalysisRepository extends JpaRepository<AiEmotionAnalysisVO, Long> {

    /**
     * 특정 사용자의 전체 일기에 대한 감정 분석 목록을 반환한다.
     *
     * @param userId 조회할 사용자 ID
     * @return 해당 사용자의 감정 분석 목록
     */
    List<AiEmotionAnalysisVO> findByDiary_User_Id(Long userId);

    /**
     * 지정된 일기 ID 목록에 해당하는 감정 분석 목록을 반환한다.
     *
     * @param diaryIds 조회할 일기 ID 목록
     * @return 해당 일기들의 감정 분석 목록
     */
    List<AiEmotionAnalysisVO> findByDiary_IdIn(List<Long> diaryIds);
}
