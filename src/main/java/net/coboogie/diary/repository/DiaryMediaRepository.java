package net.coboogie.diary.repository;

import net.coboogie.vo.DiaryMediaVO;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * {@code diary_media} 테이블에 대한 JPA Repository.
 * 일기에 첨부된 이미지/영상의 GCS URL 및 메타데이터를 관리한다.
 */
public interface DiaryMediaRepository extends JpaRepository<DiaryMediaVO, Long> {

    /**
     * 특정 일기에 첨부된 미디어 목록을 조회한다.
     */
    List<DiaryMediaVO> findByDiary_Id(Long diaryId);

    /**
     * 특정 사용자의 특정 일기에 속한 미디어를 조회한다.
     */
    Optional<DiaryMediaVO> findByIdAndDiary_IdAndDiary_User_Id(Long id, Long diaryId, Long userId);
}
