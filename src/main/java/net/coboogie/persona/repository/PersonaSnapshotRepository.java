package net.coboogie.persona.repository;

import net.coboogie.vo.PersonaSnapshotVO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * {@code persona_snapshots} 테이블에 대한 JPA Repository.
 */
public interface PersonaSnapshotRepository extends JpaRepository<PersonaSnapshotVO, Long> {

    /**
     * 특정 사용자의 가장 최근 페르소나 스냅샷을 반환한다.
     *
     * @param userId 조회할 사용자 ID
     * @return 최근 페르소나 스냅샷 (없으면 empty)
     */
    @Query("SELECT p FROM PersonaSnapshotVO p WHERE p.user.id = :userId ORDER BY p.generatedAt DESC LIMIT 1")
    Optional<PersonaSnapshotVO> findLatestByUserId(@Param("userId") Long userId);

    /**
     * 특정 사용자의 전체 페르소나 이력을 최신순으로 반환한다.
     *
     * @param userId 조회할 사용자 ID
     * @return 페르소나 이력 목록 (최신순)
     */
    List<PersonaSnapshotVO> findAllByUser_IdOrderByGeneratedAtDesc(Long userId);
}
