package net.coboogie.archive.repository;

import net.coboogie.vo.ArchiveEntryVO;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code archive_entries} 테이블에 대한 JPA Repository.
 */
public interface ArchiveEntryRepository extends JpaRepository<ArchiveEntryVO, Long> {

    /**
     * 일기 삭제 전에 해당 일기의 아카이브 연결 정보를 먼저 삭제한다.
     *
     * @param diaryId 삭제할 일기 ID
     * @return 삭제된 연결 수
     */
    long deleteByDiaryId(Long diaryId);
}
