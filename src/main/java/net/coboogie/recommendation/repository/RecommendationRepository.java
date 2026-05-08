package net.coboogie.recommendation.repository;

import net.coboogie.vo.RecommendationVO;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 추천 카드 저장소.
 */
public interface RecommendationRepository extends JpaRepository<RecommendationVO, Long> {

    /**
     * 특정 뽑기 세션의 공개 가능한 카드 목록을 조회한다.
     *
     * @param drawId 추천 뽑기 세션 ID
     * @param status 카드 상태
     * @return 카드 목록
     */
    List<RecommendationVO> findByDraw_IdAndStatusOrderByCardIndexAsc(Long drawId, RecommendationVO.Status status);

    /**
     * 특정 뽑기 세션의 카드를 사용자 소유 기준으로 조회한다.
     *
     * @param id     카드 ID
     * @param drawId 추천 뽑기 세션 ID
     * @param userId 사용자 ID
     * @return 추천 카드
     */
    Optional<RecommendationVO> findByIdAndDraw_IdAndUser_Id(Long id, Long drawId, Long userId);

    /**
     * 사용자가 공개한 추천 카드 이력을 최신순으로 조회한다.
     *
     * @param userId 사용자 ID
     * @param status 카드 상태
     * @return 공개된 추천 카드 목록
     */
    List<RecommendationVO> findByUser_IdAndStatusOrderByUpdatedAtDesc(Long userId, RecommendationVO.Status status);

    /**
     * 특정 뽑기 세션에서 공개된 카드 목록을 최신순으로 조회한다.
     *
     * @param drawId 추천 뽑기 세션 ID
     * @param status 카드 상태
     * @return 공개된 카드 목록
     */
    List<RecommendationVO> findByDraw_IdAndStatusOrderByUpdatedAtDesc(Long drawId, RecommendationVO.Status status);

    /**
     * 특정 기간에 생성된 shuffle 카드 개수를 조회한다.
     *
     * @param userId         사용자 ID
     * @param generationType 생성 유형
     * @param startAt        조회 시작 시각
     * @param endAt          조회 종료 시각
     * @return 생성된 shuffle 카드 수
     */
    long countByUser_IdAndGenerationTypeAndCreatedAtBetween(
            Long userId,
            RecommendationVO.GenerationType generationType,
            LocalDateTime startAt,
            LocalDateTime endAt);

    /**
     * 지정한 상태의 카드 목록을 조회한다.
     *
     * @param drawId   추천 뽑기 세션 ID
     * @param statuses 카드 상태 목록
     * @return 카드 목록
     */
    List<RecommendationVO> findByDraw_IdAndStatusInOrderByCardIndexAsc(
            Long drawId,
            Collection<RecommendationVO.Status> statuses);
}
