package net.coboogie.archive.repository;

import net.coboogie.vo.ArchiveDiaryVO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * {@code archive_diary} 테이블에 대한 JPA Repository.
 */
public interface ArchiveDiaryRepository extends JpaRepository<ArchiveDiaryVO, Long> {

    /**
     * 폴더 내 일기 목록을 추가일 내림차순으로 반환한다.
     *
     * @param folderId 폴더 ID
     * @return 폴더 내 연결 엔티티 목록
     */
    List<ArchiveDiaryVO> findAllByFolder_IdOrderByAddedAtDesc(Long folderId);

    /**
     * 폴더 내 일기와 첨부 미디어를 함께 조회한다.
     * 응답 DTO 변환 중 diary/media lazy loading으로 발생하는 N+1 쿼리를 줄이기 위한 목록 조회 전용 메서드이다.
     */
    @Query("""
            SELECT DISTINCT ad
            FROM ArchiveDiaryVO ad
            JOIN FETCH ad.diary d
            LEFT JOIN FETCH d.media
            WHERE ad.folder.id = :folderId
            ORDER BY ad.addedAt DESC
            """)
    List<ArchiveDiaryVO> findAllWithDiaryAndMediaByFolderId(@Param("folderId") Long folderId);

    /**
     * 폴더-일기 연결 단건을 조회한다.
     *
     * @param folderId 폴더 ID
     * @param diaryId  일기 ID
     * @return 연결 엔티티 (없으면 empty)
     */
    Optional<ArchiveDiaryVO> findByFolder_IdAndDiary_Id(Long folderId, Long diaryId);

    /**
     * 폴더-일기 연결이 존재하는지 확인한다.
     *
     * @param folderId 폴더 ID
     * @param diaryId  일기 ID
     * @return 존재 여부
     */
    boolean existsByFolder_IdAndDiary_Id(Long folderId, Long diaryId);

    /**
     * 폴더 삭제 전에 폴더와 일기의 연결 정보를 먼저 삭제한다.
     *
     * @param folderId 삭제할 폴더 ID
     * @return 삭제된 연결 수
     */
    long deleteByFolder_Id(Long folderId);

    /**
     * 일기 삭제 전에 해당 일기의 폴더 연결 정보를 먼저 삭제한다.
     *
     * @param diaryId 삭제할 일기 ID
     * @return 삭제된 연결 수
     */
    long deleteByDiaryId(Long diaryId);
}
