package net.coboogie.diary.repository;

import net.coboogie.fillybackend.vo.DiaryEntryVO;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * {@code diary_entries} 테이블에 대한 JPA Repository.
 * 일기 항목의 저장, 조회, 수정, 삭제를 담당한다.
 */
public interface DiaryEntryRepository extends JpaRepository<DiaryEntryVO, Long> {

    /**
     * 일기 ID와 작성자 ID로 일기를 조회한다.
     * 다른 사용자의 일기에 접근하는 것을 방지하기 위해 userId를 함께 확인한다.
     */
    Optional<DiaryEntryVO> findByIdAndUser_Id(Long id, Long userId);
}
