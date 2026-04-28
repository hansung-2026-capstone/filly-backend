package net.coboogie.stat.repository;

import net.coboogie.vo.MonthlyStatVO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * {@code monthly_stats} 테이블에 대한 JPA Repository.
 */
public interface MonthlyStatRepository extends JpaRepository<MonthlyStatVO, Long> {

    /**
     * 사용자 ID와 연월로 월별 통계를 조회한다.
     *
     * @param userId      조회할 사용자 ID
     * @param recordMonth {@code "YYYY-MM"} 형식의 연월 문자열
     * @return 해당 월의 통계 (없으면 empty)
     */
    @Query("SELECT m FROM MonthlyStatVO m WHERE m.user.id = :userId AND m.recordMonth = :recordMonth")
    Optional<MonthlyStatVO> findByUserIdAndRecordMonth(@Param("userId") Long userId, @Param("recordMonth") String recordMonth);
}
