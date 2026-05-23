package net.coboogie.recommendation.repository;

import jakarta.persistence.LockModeType;
import net.coboogie.vo.RecommendationDrawVO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

/**
 * 추천 뽑기 세션 저장소.
 */
public interface RecommendationDrawRepository extends JpaRepository<RecommendationDrawVO, Long> {

    /**
     * 사용자의 최신 추천 뽑기 세션을 상태 목록 기준으로 조회한다.
     *
     * @param userId   사용자 ID
     * @param statuses 세션 상태 목록
     * @return 최신 추천 뽑기 세션
     */
    Optional<RecommendationDrawVO> findTopByUserIdAndStatusInOrderByUpdatedAtDesc(
            Long userId,
            Collection<RecommendationDrawVO.Status> statuses);

    /**
     * 사용자 소유의 추천 뽑기 세션을 조회한다.
     *
     * @param id     추천 뽑기 세션 ID
     * @param userId 사용자 ID
     * @return 추천 뽑기 세션
     */
    Optional<RecommendationDrawVO> findByIdAndUserId(Long id, Long userId);

    /**
     * 사용자 소유의 추천 뽑기 세션을 쓰기 잠금으로 조회한다.
     *
     * @param id     추천 뽑기 세션 ID
     * @param userId 사용자 ID
     * @return 추천 뽑기 세션
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM RecommendationDrawVO d WHERE d.id = :id AND d.user.id = :userId")
    Optional<RecommendationDrawVO> findLockedByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
