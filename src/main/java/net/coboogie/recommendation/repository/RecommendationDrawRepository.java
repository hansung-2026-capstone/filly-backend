package net.coboogie.recommendation.repository;

import net.coboogie.vo.RecommendationDrawVO;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 추천 뽑기 세션 저장소.
 */
public interface RecommendationDrawRepository extends JpaRepository<RecommendationDrawVO, Long> {

    /**
     * 사용자 소유의 추천 뽑기 세션을 조회한다.
     *
     * @param id     추천 뽑기 세션 ID
     * @param userId 사용자 ID
     * @return 추천 뽑기 세션
     */
    Optional<RecommendationDrawVO> findByIdAndUser_Id(Long id, Long userId);
}
