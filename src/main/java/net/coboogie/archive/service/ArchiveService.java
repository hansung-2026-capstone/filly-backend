package net.coboogie.archive.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coboogie.archive.dto.ArchiveDiaryAddRequest;
import net.coboogie.archive.dto.ArchiveFolderCreateRequest;
import net.coboogie.archive.dto.ArchiveFolderResponse;
import net.coboogie.archive.dto.ArchiveFolderUpdateRequest;
import net.coboogie.archive.repository.ArchiveDiaryRepository;
import net.coboogie.archive.repository.ArchiveFolderRepository;
import net.coboogie.diary.dto.DiaryResponse;
import net.coboogie.diary.service.GcsStorageService;
import net.coboogie.diary.repository.DiaryEntryRepository;
import net.coboogie.vo.ArchiveDiaryVO;
import net.coboogie.vo.ArchiveFolderVO;
import net.coboogie.vo.DiaryEntryVO;
import net.coboogie.vo.UserVO;
import net.coboogie.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * 아카이브 폴더 생성·조회·수정·삭제 및 폴더-일기 연결 관리 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArchiveService {

    private final ArchiveFolderRepository archiveFolderRepository;
    private final ArchiveDiaryRepository archiveDiaryRepository;
    private final DiaryEntryRepository diaryEntryRepository;
    private final UserRepository userRepository;
    private final GcsStorageService gcsStorageService;

    // ──────────────────────────── 폴더 CRUD ────────────────────────────

    /**
     * 아카이브 폴더를 생성한다.
     *
     * @param userId  인증 사용자 ID
     * @param request 폴더 이름·색상
     * @return 생성된 폴더 응답 DTO (diaryCount=0)
     */
    @Transactional
    public ArchiveFolderResponse createFolder(Long userId, ArchiveFolderCreateRequest request) {
        UserVO user = findUser(userId);
        ArchiveFolderVO folder = ArchiveFolderVO.builder()
                .user(user)
                .name(request.name())
                .color(request.color())
                .build();
        ArchiveFolderVO saved = archiveFolderRepository.save(folder);
        log.info("아카이브 폴더 생성: userId={}, folderId={}", userId, saved.getId());
        return ArchiveFolderResponse.from(saved, 0L);
    }

    /**
     * 사용자의 폴더 목록을 일기 수와 함께 반환한다.
     *
     * @param userId 인증 사용자 ID
     * @return 폴더 목록 (최신순)
     */
    @Transactional(readOnly = true)
    public List<ArchiveFolderResponse> getFolders(Long userId) {
        List<ArchiveFolderVO> folders = archiveFolderRepository.findAllByUser_IdOrderByCreatedAtDesc(userId);

        Map<Long, Long> countMap = archiveFolderRepository.countDiariesGroupByFolder(userId)
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));

        return folders.stream()
                .map(f -> ArchiveFolderResponse.from(f, countMap.getOrDefault(f.getId(), 0L)))
                .toList();
    }

    /**
     * 폴더 이름과 색상을 수정한다. null인 필드는 기존 값을 유지한다.
     *
     * @param userId   인증 사용자 ID
     * @param folderId 수정할 폴더 ID
     * @param request  변경할 이름·색상
     * @return 수정된 폴더 응답 DTO
     * @throws NoSuchElementException 폴더가 없거나 본인 소유가 아닌 경우
     */
    @Transactional
    public ArchiveFolderResponse updateFolder(Long userId, Long folderId, ArchiveFolderUpdateRequest request) {
        ArchiveFolderVO folder = findFolder(folderId, userId);
        if (request.name() != null) {
            folder.setName(request.name());
        }
        if (request.color() != null) {
            folder.setColor(request.color());
        }
        long diaryCount = archiveDiaryRepository.findAllByFolder_IdOrderByAddedAtDesc(folderId).size();
        return ArchiveFolderResponse.from(folder, diaryCount);
    }

    /**
     * 폴더를 삭제한다. 폴더 내 일기 연결도 cascade로 함께 삭제된다.
     *
     * @param userId   인증 사용자 ID
     * @param folderId 삭제할 폴더 ID
     * @throws NoSuchElementException 폴더가 없거나 본인 소유가 아닌 경우
     */
    @Transactional
    public void deleteFolder(Long userId, Long folderId) {
        ArchiveFolderVO folder = findFolder(folderId, userId);
        archiveFolderRepository.delete(folder);
        log.info("아카이브 폴더 삭제: userId={}, folderId={}", userId, folderId);
    }

    // ──────────────────────────── 폴더 내 일기 ────────────────────────────

    /**
     * 폴더 내 일기 목록을 반환한다.
     *
     * @param userId   인증 사용자 ID
     * @param folderId 조회할 폴더 ID
     * @return 폴더 내 일기 목록 (추가일 내림차순)
     * @throws NoSuchElementException 폴더가 없거나 본인 소유가 아닌 경우
     */
    @Transactional(readOnly = true)
    public List<DiaryResponse> getDiariesInFolder(Long userId, Long folderId) {
        findFolder(folderId, userId);
        return archiveDiaryRepository.findAllByFolder_IdOrderByAddedAtDesc(folderId)
                .stream()
                .map(ad -> DiaryResponse.from(ad.getDiary(), gcsStorageService::generateSignedUrl))
                .toList();
    }

    /**
     * 폴더에 일기를 추가한다. 이미 존재하는 경우 예외를 던진다.
     *
     * @param userId   인증 사용자 ID
     * @param folderId 대상 폴더 ID
     * @param request  추가할 일기 ID
     * @throws NoSuchElementException   폴더 또는 일기가 없거나 본인 소유가 아닌 경우
     * @throws IllegalStateException    이미 해당 폴더에 추가된 일기인 경우
     */
    @Transactional
    public void addDiaryToFolder(Long userId, Long folderId, ArchiveDiaryAddRequest request) {
        ArchiveFolderVO folder = findFolder(folderId, userId);
        DiaryEntryVO diary = diaryEntryRepository.findByIdAndUser_Id(request.diaryId(), userId)
                .orElseThrow(() -> new NoSuchElementException("일기를 찾을 수 없습니다: " + request.diaryId()));

        if (archiveDiaryRepository.existsByFolder_IdAndDiary_Id(folderId, request.diaryId())) {
            throw new IllegalStateException("이미 해당 폴더에 추가된 일기입니다.");
        }

        ArchiveDiaryVO link = ArchiveDiaryVO.builder()
                .folder(folder)
                .diary(diary)
                .build();
        archiveDiaryRepository.save(link);
        log.info("아카이브 일기 추가: userId={}, folderId={}, diaryId={}", userId, folderId, request.diaryId());
    }

    /**
     * 폴더에서 일기를 제거한다.
     *
     * @param userId   인증 사용자 ID
     * @param folderId 대상 폴더 ID
     * @param diaryId  제거할 일기 ID
     * @throws NoSuchElementException 폴더가 없거나 연결이 존재하지 않는 경우
     */
    @Transactional
    public void removeDiaryFromFolder(Long userId, Long folderId, Long diaryId) {
        findFolder(folderId, userId);
        ArchiveDiaryVO link = archiveDiaryRepository.findByFolder_IdAndDiary_Id(folderId, diaryId)
                .orElseThrow(() -> new NoSuchElementException("해당 폴더에 일기가 없습니다."));
        archiveDiaryRepository.delete(link);
        log.info("아카이브 일기 제거: userId={}, folderId={}, diaryId={}", userId, folderId, diaryId);
    }

    // ──────────────────────────── 내부 헬퍼 ────────────────────────────

    private UserVO findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다: " + userId));
    }

    private ArchiveFolderVO findFolder(Long folderId, Long userId) {
        return archiveFolderRepository.findByIdAndUser_Id(folderId, userId)
                .orElseThrow(() -> new NoSuchElementException("폴더를 찾을 수 없습니다: " + folderId));
    }
}
