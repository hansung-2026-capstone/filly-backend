package net.coboogie.archive.repository;

import net.coboogie.vo.ArchiveFolderVO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * {@code archive_folder} 테이블에 대한 JPA Repository.
 */
public interface ArchiveFolderRepository extends JpaRepository<ArchiveFolderVO, Long> {

    /**
     * 사용자의 폴더 목록을 생성일 내림차순으로 반환한다.
     *
     * @param userId 사용자 ID
     * @return 폴더 목록 (최신순)
     */
    List<ArchiveFolderVO> findAllByUser_IdOrderByCreatedAtDesc(Long userId);

    /**
     * 폴더 ID와 사용자 ID로 폴더를 조회한다.
     * 다른 사용자의 폴더에 접근하는 것을 방지한다.
     *
     * @param id     폴더 ID
     * @param userId 사용자 ID
     * @return 폴더 (없으면 empty)
     */
    Optional<ArchiveFolderVO> findByIdAndUser_Id(Long id, Long userId);

    /**
     * 사용자의 각 폴더별 일기 수를 조회한다.
     *
     * @param userId 사용자 ID
     * @return [folderId, diaryCount] 형태의 Object 배열 목록
     */
    @Query("SELECT ad.folder.id, COUNT(ad.id) FROM ArchiveDiaryVO ad " +
            "WHERE ad.folder.user.id = :userId GROUP BY ad.folder.id")
    List<Object[]> countDiariesGroupByFolder(@Param("userId") Long userId);
}
