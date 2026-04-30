package net.coboogie.diary.repository;

import net.coboogie.vo.AiEmotionAnalysisVO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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

    /**
     * 특정 사용자의 지정 기간 내 일기에 대한 감정 분석 목록을 반환한다.
     *
     * @param userId    조회할 사용자 ID
     * @param startDate 기간 시작일 (inclusive)
     * @param endDate   기간 종료일 (inclusive)
     * @return 해당 기간의 감정 분석 목록
     */
    @Query("SELECT a FROM AiEmotionAnalysisVO a WHERE a.diary.user.id = :userId " +
            "AND a.diary.writtenAt BETWEEN :startDate AND :endDate")
    List<AiEmotionAnalysisVO> findByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
