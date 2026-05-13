package net.coboogie.recommendation.repository;

import jakarta.persistence.LockModeType;
import net.coboogie.vo.RecommendationDrawVO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

/**
 * 추천 뽑기 세션 저장소.
 */
public interface RecommendationDrawRepository extends JpaRepository<RecommendationDrawVO, Long> {

    /**
     * 사용자의 최신 활성 추천 뽑기 세션을 조회한다.
     *
     * @param userId 사용자 ID
     * @param status 세션 상태
     * @return 최신 추천 뽑기 세션
     */
    Optional<RecommendationDrawVO> findTopByUser_IdAndStatusOrderByUpdatedAtDesc(
            Long userId,
            RecommendationDrawVO.Status status);

    /**
     * 사용자 소유의 추천 뽑기 세션을 조회한다.
     *
     * @param id     추천 뽑기 세션 ID
     * @param userId 사용자 ID
     * @return 추천 뽑기 세션
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RecommendationDrawVO> findByIdAndUser_Id(Long id, Long userId);
}
