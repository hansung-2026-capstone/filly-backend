package net.coboogie.diary.repository;

import net.coboogie.vo.DiaryEntryVO;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
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

    /**
     * 특정 사용자의 일기 중 작성 날짜가 지정된 기간 내에 있는 목록을 작성일 오름차순으로 반환한다.
     * 월별 목록 조회 시 해당 월의 첫째 날~마지막 날을 범위로 사용한다.
     *
     * @param userId    조회할 사용자 ID
     * @param startDate 기간 시작일 (inclusive)
     * @param endDate   기간 종료일 (inclusive)
     * @return 해당 기간의 일기 목록 (작성일 오름차순)
     */
    List<DiaryEntryVO> findByUser_IdAndWrittenAtBetweenOrderByWrittenAtAsc(
            Long userId, LocalDate startDate, LocalDate endDate);

    /**
     * 특정 사용자의 전체 일기 목록을 반환한다.
     *
     * @param userId 조회할 사용자 ID
     * @return 해당 사용자의 전체 일기 목록
     */
    List<DiaryEntryVO> findAllByUser_Id(Long userId);

    /**
     * 특정 사용자의 지정 기간 내 일기 수를 반환한다.
     *
     * @param userId    조회할 사용자 ID
     * @param startDate 기간 시작일 (inclusive)
     * @param endDate   기간 종료일 (inclusive)
     * @return 해당 기간의 일기 수
     */
    int countByUser_IdAndWrittenAtBetween(Long userId, LocalDate startDate, LocalDate endDate);
}
