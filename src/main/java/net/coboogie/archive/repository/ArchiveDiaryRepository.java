package net.coboogie.archive.repository;

import net.coboogie.vo.ArchiveDiaryVO;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
