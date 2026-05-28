package net.coboogie.diary.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coboogie.archive.repository.ArchiveDiaryRepository;
import net.coboogie.archive.repository.ArchiveEntryRepository;
import net.coboogie.diary.dto.AiDraftResult;
import net.coboogie.diary.dto.DiaryDraftCommand;
import net.coboogie.diary.dto.DiaryDraftResponse;
import net.coboogie.diary.dto.DiarySaveCommand;
import net.coboogie.diary.dto.DiaryResponse;
import net.coboogie.diary.dto.DiaryUpdateRequest;
import net.coboogie.diary.repository.AiDiaryResultRepository;
import net.coboogie.diary.repository.AiEmotionAnalysisRepository;
import net.coboogie.diary.repository.DiaryEntryRepository;
import net.coboogie.diary.repository.DiaryMediaRepository;
import net.coboogie.stat.repository.MonthlyStatRepository;
import net.coboogie.vo.AiDiaryResultVO;
import net.coboogie.vo.AiEmotionAnalysisVO;
import net.coboogie.vo.DiaryEntryVO;
import net.coboogie.vo.DiaryMediaVO;
import net.coboogie.vo.UserVO;
import net.coboogie.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import ws.schild.jave.MultimediaObject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * 일기 도메인 핵심 비즈니스 로직 서비스.
 * <p>
 * 구현 완료: AI 초안 생성, 일기 저장, 단건 조회, 월별 목록 조회, 수정, 삭제
 * 예정 구현: 목록 조회, 단건 조회, 수정, 삭제
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryService {

    private static final int MAX_DRAFT_IMAGE_COUNT = 4;
    private static final long MAX_DRAFT_VOICE_DURATION_MS = 60_000L;
    private static final List<String> SUPPORTED_DRAFT_IMAGE_TYPES =
            List.of("image/jpeg", "image/jpg", "image/png", "image/webp", "image/heic", "image/heif");
    private static final List<String> SUPPORTED_DRAFT_AUDIO_TYPES =
            List.of("audio/mpeg", "audio/mp3", "audio/wav", "audio/x-wav", "audio/flac",
                    "audio/ogg", "audio/webm", "audio/mp4", "audio/m4a", "audio/x-m4a", "audio/aac");

    private final GcsStorageService gcsStorageService;
    private final AiDraftGeneratorService aiDraftGeneratorService;
    private final UserRepository userRepository;
    private final DiaryEntryRepository diaryEntryRepository;
    private final DiaryMediaRepository diaryMediaRepository;
    private final AiEmotionAnalysisRepository aiEmotionAnalysisRepository;
    private final AiDiaryResultRepository aiDiaryResultRepository;
    private final MonthlyStatRepository monthlyStatRepository;
    private final ArchiveDiaryRepository archiveDiaryRepository;
    private final ArchiveEntryRepository archiveEntryRepository;
    private final DiaryAnalysisAsyncService diaryAnalysisAsyncService;
    private final ObjectMapper objectMapper;

    /**
     * 일기를 DB에 저장하고 저장된 결과를 반환한다.
     * <p>
     * rawContent(텍스트)를 diary_entries에 저장하고, 이미지가 있으면 GCS에 업로드하여 diary_media에 저장한다.
     * 작성 날짜와 이모지를 함께 저장하며, 별점은 초기에 설정되지 않는다.<br>
     * {@code aiAnalysis}가 있으면 {@code ai_diary_analysis}에 감정 분석 결과를 저장한다.<br>
     * {@code generatedText}가 있으면 {@code ai_diary_results}에 AI 생성 텍스트를 저장한다.
     *
     * @param command userId, rawContent, emoji, writtenAt, images, aiAnalysis, generatedText를 담은 커맨드 객체
     * @return 저장된 일기의 응답 DTO (mediaUrls 포함)
     * @throws IllegalArgumentException 존재하지 않는 userId인 경우
     * @throws UncheckedIOException     GCS 업로드 실패 시
     */
    @Transactional
    public DiaryResponse saveDiary(DiarySaveCommand command) {
        UserVO user = userRepository.findById(command.userId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + command.userId()));
        validateSaveCommand(command);

        DiaryEntryVO diary = DiaryEntryVO.builder()
                .user(user)
                .rawContent(command.rawContent())
                .emoji(command.emoji())
                .writtenAt(command.writtenAt())
                .build();

        DiaryEntryVO saved = diaryEntryRepository.save(diary);

        List<DiaryMediaVO> savedMedia = saveMediaFiles(saved, command.images());
        saved.setMedia(savedMedia);

        DiaryDraftResponse.AiAnalysis aiAnalysis = command.aiAnalysis();
        if (aiAnalysis == null) {
            scheduleAnalysisAfterCommit(saved.getId(), command, user);
        } else {
            saveEmotionAnalysis(saved, aiAnalysis);
        }
        if (command.generatedText() != null && !command.generatedText().isBlank()) {
            aiDiaryResultRepository.save(AiDiaryResultVO.builder()
                    .diary(saved)
                    .generatedText(command.generatedText())
                    .build());
        }

        invalidateMonthlyStat(command.userId(), saved.getWrittenAt());

        return DiaryResponse.from(saved, gcsStorageService::generateSignedUrl);
    }

    private void validateSaveCommand(DiarySaveCommand command) {
        if (command.emoji() == null || command.emoji().isBlank()) {
            throw new IllegalArgumentException("emoji는 필수입니다.");
        }
        if (command.writtenAt() == null) {
            throw new IllegalArgumentException("writtenAt은 필수입니다.");
        }
        if (!hasTextContent(command) && !hasImageContent(command)) {
            throw new IllegalArgumentException("rawContent 또는 images 중 하나는 필수입니다.");
        }
    }

    private boolean hasTextContent(DiarySaveCommand command) {
        return command.rawContent() != null && !command.rawContent().isBlank();
    }

    private boolean hasImageContent(DiarySaveCommand command) {
        return command.images() != null
                && command.images().stream().anyMatch(image -> image != null && !image.isEmpty());
    }

    /**
     * AI 감정 분석 결과를 {@code ai_diary_analysis} 테이블에 저장한다.
     * JSON 직렬화 실패 시 경고 로그를 남기고 저장을 건너뛴다.
     *
     * @param diary    저장할 일기 엔티티
     * @param analysis 저장할 감정 분석 결과
     */
    private void saveEmotionAnalysis(DiaryEntryVO diary, DiaryDraftResponse.AiAnalysis analysis) {
        try {
            AiEmotionAnalysisVO vo = AiEmotionAnalysisVO.builder()
                    .diary(diary)
                    .emotions(objectMapper.writeValueAsString(analysis.emotions()))
                    .happinessIndex(analysis.happinessIndex())
                    .activities(objectMapper.writeValueAsString(analysis.activities()))
                    .places(objectMapper.writeValueAsString(analysis.places()))
                    .people(objectMapper.writeValueAsString(analysis.people()))
                    .iabCategories(objectMapper.writeValueAsString(analysis.iabCategories()))
                    .patterns(objectMapper.writeValueAsString(analysis.patterns()))
                    .moodSummary(analysis.moodSummary())
                    .tone(analysis.tone())
                    .build();
            aiEmotionAnalysisRepository.save(vo);
        } catch (JsonProcessingException e) {
            log.warn("감정 분석 직렬화 실패: diaryId={}", diary.getId(), e);
        }
    }

    private void scheduleAnalysisAfterCommit(Long diaryId, DiarySaveCommand command, UserVO user) {
        List<DiaryAnalysisAsyncService.AnalysisImage> images = copyAnalysisImages(command.images());
        Runnable task = () -> diaryAnalysisAsyncService.analyzeAndSaveAsync(
                diaryId,
                command.rawContent(),
                command.writtenAt(),
                user.getGender(),
                user.getAgeGroup(),
                user.getAiDraftTone(),
                images
        );

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
            return;
        }
        task.run();
    }

    private List<DiaryAnalysisAsyncService.AnalysisImage> copyAnalysisImages(List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            return Collections.emptyList();
        }
        List<DiaryAnalysisAsyncService.AnalysisImage> copies = new ArrayList<>();
        for (MultipartFile image : images) {
            if (image == null || image.isEmpty()) {
                continue;
            }
            try {
                copies.add(new DiaryAnalysisAsyncService.AnalysisImage(
                        image.getBytes(),
                        image.getOriginalFilename(),
                        image.getContentType()
                ));
            } catch (IOException e) {
                log.warn("AI 분석용 이미지 복사 실패 filename={}", image.getOriginalFilename(), e);
            }
        }
        return copies;
    }

    /**
     * 이미지 파일 목록을 GCS에 업로드하고 {@code diary_media} 테이블에 저장한다.
     * 이미지가 없으면 빈 리스트를 반환한다.
     *
     * @param diary  미디어를 연결할 일기 엔티티
     * @param images 업로드할 이미지 파일 목록
     * @return 저장된 {@link DiaryMediaVO} 목록
     * @throws UncheckedIOException GCS 업로드 실패 시
     */
    private List<DiaryMediaVO> saveMediaFiles(DiaryEntryVO diary, List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            return Collections.emptyList();
        }
        List<DiaryMediaVO> result = new ArrayList<>();
        for (MultipartFile image : images) {
            if (image == null || image.isEmpty()) {
                continue;
            }
            try {
                String url = gcsStorageService.upload(image, "uploads/images");
                DiaryMediaVO media = DiaryMediaVO.builder()
                        .diary(diary)
                        .type(DiaryMediaVO.Type.IMAGE)
                        .gcsUrl(url)
                        .fileSize((int) image.getSize())
                        .build();
                result.add(diaryMediaRepository.save(media));
            } catch (IOException e) {
                throw new UncheckedIOException("이미지 업로드 실패: " + image.getOriginalFilename(), e);
            }
        }
        return result;
    }

    /**
     * 기존 일기에 이미지 파일을 추가한다.
     *
     * @param diaryId 추가 대상 일기 ID
     * @param userId  JWT 인증 사용자 ID
     * @param images  추가할 이미지 파일 목록
     * @return 파일 추가 후 일기 응답 DTO
     */
    @Transactional
    public DiaryResponse addDiaryMedia(Long diaryId, Long userId, List<MultipartFile> images) {
        DiaryEntryVO diary = diaryEntryRepository.findByIdAndUser_Id(diaryId, userId)
                .orElseThrow(() -> new NoSuchElementException("일기를 찾을 수 없습니다: " + diaryId));
        validateImages(images);

        List<DiaryMediaVO> addedMedia = saveMediaFiles(diary, images);
        if (diary.getMedia() == null) {
            diary.setMedia(new ArrayList<>());
        }
        diary.getMedia().addAll(addedMedia);
        diary.setUpdatedAt(LocalDateTime.now());
        invalidateMonthlyStat(userId, diary.getWrittenAt());

        return DiaryResponse.from(diary, gcsStorageService::generateSignedUrl);
    }

    /**
     * 기존 일기의 특정 이미지 파일을 새 파일로 교체한다.
     *
     * @param diaryId  수정 대상 일기 ID
     * @param userId   JWT 인증 사용자 ID
     * @param mediaId  교체 대상 미디어 ID
     * @param image    새 이미지 파일
     * @return 파일 교체 후 일기 응답 DTO
     */
    @Transactional
    public DiaryResponse replaceDiaryMedia(Long diaryId, Long userId, Long mediaId, MultipartFile image) {
        DiaryEntryVO diary = diaryEntryRepository.findByIdAndUser_Id(diaryId, userId)
                .orElseThrow(() -> new NoSuchElementException("일기를 찾을 수 없습니다: " + diaryId));
        DiaryMediaVO media = diaryMediaRepository.findByIdAndDiary_IdAndDiary_User_Id(mediaId, diaryId, userId)
                .orElseThrow(() -> new NoSuchElementException("미디어를 찾을 수 없습니다: " + mediaId));
        validateImage(image);

        String oldBlobName = media.getGcsUrl();
        try {
            String newBlobName = gcsStorageService.upload(image, "uploads/images");
            media.setGcsUrl(newBlobName);
            media.setFileSize((int) image.getSize());
            media.setType(DiaryMediaVO.Type.IMAGE);
            diary.setUpdatedAt(LocalDateTime.now());
            gcsStorageService.delete(oldBlobName);
        } catch (IOException e) {
            throw new UncheckedIOException("이미지 업로드 실패: " + image.getOriginalFilename(), e);
        }

        invalidateMonthlyStat(userId, diary.getWrittenAt());
        return DiaryResponse.from(diary, gcsStorageService::generateSignedUrl);
    }

    /**
     * 기존 일기의 특정 이미지 파일을 삭제한다.
     *
     * @param diaryId 삭제 대상 일기 ID
     * @param userId  JWT 인증 사용자 ID
     * @param mediaId 삭제 대상 미디어 ID
     * @return 파일 삭제 후 일기 응답 DTO
     */
    @Transactional
    public DiaryResponse deleteDiaryMedia(Long diaryId, Long userId, Long mediaId) {
        DiaryEntryVO diary = diaryEntryRepository.findByIdAndUser_Id(diaryId, userId)
                .orElseThrow(() -> new NoSuchElementException("일기를 찾을 수 없습니다: " + diaryId));
        DiaryMediaVO media = diaryMediaRepository.findByIdAndDiary_IdAndDiary_User_Id(mediaId, diaryId, userId)
                .orElseThrow(() -> new NoSuchElementException("미디어를 찾을 수 없습니다: " + mediaId));

        String blobName = media.getGcsUrl();
        if (diary.getMedia() != null) {
            diary.getMedia().removeIf(item -> Objects.equals(item.getId(), mediaId));
        }
        diaryMediaRepository.delete(media);
        gcsStorageService.delete(blobName);
        diary.setUpdatedAt(LocalDateTime.now());
        invalidateMonthlyStat(userId, diary.getWrittenAt());

        return DiaryResponse.from(diary, gcsStorageService::generateSignedUrl);
    }

    private void validateImages(List<MultipartFile> images) {
        if (images == null || images.stream().noneMatch(image -> image != null && !image.isEmpty())) {
            throw new IllegalArgumentException("images는 하나 이상 필요합니다.");
        }
    }

    private void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("image는 필수입니다.");
        }
    }

    /**
     * 일기 단건을 조회하여 반환한다.
     * <p>
     * 본인 소유의 일기만 조회할 수 있다. 존재하지 않거나 다른 사용자 소유이면 예외가 발생한다.
     *
     * @param diaryId 조회할 일기 ID
     * @param userId  JWT 인증 사용자 ID
     * @return 조회된 일기 응답 DTO
     * @throws NoSuchElementException 일기가 존재하지 않거나 본인 소유가 아닌 경우
     */
    @Transactional(readOnly = true)
    public DiaryResponse getDiary(Long diaryId, Long userId) {
        DiaryEntryVO diary = diaryEntryRepository.findByIdAndUser_Id(diaryId, userId)
                .orElseThrow(() -> new NoSuchElementException("일기를 찾을 수 없습니다: " + diaryId));
        return DiaryResponse.from(diary, gcsStorageService::generateSignedUrl);
    }

    /**
     * 특정 연월의 일기 목록을 조회하여 반환한다.
     * <p>
     * 해당 월의 첫째 날부터 마지막 날까지 범위로 조회하며, 작성일 오름차순으로 정렬된다.
     *
     * @param userId JWT 인증 사용자 ID
     * @param year   조회 연도
     * @param month  조회 월 (1~12)
     * @return 해당 월의 일기 목록 (작성일 오름차순)
     */
    @Transactional(readOnly = true)
    public List<DiaryResponse> getDiariesByMonth(Long userId, int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        return diaryEntryRepository
                .findWithMediaByUser_IdAndWrittenAtBetweenOrderByWrittenAtAsc(userId, startDate, endDate)
                .stream()
                .map(d -> DiaryResponse.fromSummary(d, gcsStorageService::generateSignedUrl))
                .toList();
    }

    /**
     * 일기의 rawContent와 emoji를 수정하고 수정된 결과를 반환한다.
     * <p>
     * 본인 소유의 일기만 수정할 수 있다. 각 필드가 null이면 기존 값을 유지한다.
     *
     * @param diaryId 수정할 일기 ID
     * @param userId  JWT 인증 사용자 ID
     * @param request 수정할 rawContent, emoji
     * @return 수정된 일기 응답 DTO
     * @throws NoSuchElementException 일기가 존재하지 않거나 본인 소유가 아닌 경우
     */
    @Transactional
    public DiaryResponse updateDiary(Long diaryId, Long userId, DiaryUpdateRequest request) {
        DiaryEntryVO diary = diaryEntryRepository.findByIdAndUser_Id(diaryId, userId)
                .orElseThrow(() -> new NoSuchElementException("일기를 찾을 수 없습니다: " + diaryId));

        if (request.rawContent() != null) {
            diary.setRawContent(request.rawContent());
        }
        if (request.emoji() != null) {
            diary.setEmoji(request.emoji());
        }
        diary.setUpdatedAt(LocalDateTime.now());

        invalidateMonthlyStat(userId, diary.getWrittenAt());

        return DiaryResponse.from(diary, gcsStorageService::generateSignedUrl);
    }

    /**
     * 일기의 별점을 업데이트한다.
     * <p>
     * 본인 소유의 일기만 수정할 수 있다.
     *
     * @param diaryId    수정할 일기 ID
     * @param userId     JWT 인증 사용자 ID
     * @param starRating 저장할 별점 (1~5)
     * @return 수정된 일기 응답 DTO
     * @throws NoSuchElementException   일기가 존재하지 않거나 본인 소유가 아닌 경우
     * @throws IllegalArgumentException 별점이 1~5 범위를 벗어난 경우
     */
    @Transactional
    public DiaryResponse updateStarRating(Long diaryId, Long userId, Integer starRating) {
        if (starRating == null || starRating < 1 || starRating > 5) {
            throw new IllegalArgumentException("별점은 1~5 사이여야 합니다.");
        }
        DiaryEntryVO diary = diaryEntryRepository.findByIdAndUser_Id(diaryId, userId)
                .orElseThrow(() -> new NoSuchElementException("일기를 찾을 수 없습니다: " + diaryId));

        diary.setStarRating(starRating);
        diary.setUpdatedAt(LocalDateTime.now());

        return DiaryResponse.from(diary, gcsStorageService::generateSignedUrl);
    }

    /**
     * 일기를 삭제한다.
     * <p>
     * 본인 소유의 일기만 삭제할 수 있다. 존재하지 않거나 다른 사용자 소유이면 예외가 발생한다.
     *
     * @param diaryId 삭제할 일기 ID
     * @param userId  JWT 인증 사용자 ID
     * @throws NoSuchElementException 일기가 존재하지 않거나 본인 소유가 아닌 경우
     */
    @Transactional
    public void deleteDiary(Long diaryId, Long userId) {
        DiaryEntryVO diary = diaryEntryRepository.findByIdAndUser_Id(diaryId, userId)
                .orElseThrow(() -> new NoSuchElementException("일기를 찾을 수 없습니다: " + diaryId));
        invalidateMonthlyStat(userId, diary.getWrittenAt());
        deleteDiaryReferences(diaryId);
        deleteMediaBlobs(diary);
        diaryEntryRepository.delete(diary);
    }

    private void deleteDiaryReferences(Long diaryId) {
        aiEmotionAnalysisRepository.deleteByDiaryId(diaryId);
        aiDiaryResultRepository.deleteByDiaryId(diaryId);
        archiveDiaryRepository.deleteByDiaryId(diaryId);
        archiveEntryRepository.deleteByDiaryId(diaryId);
    }

    private void deleteMediaBlobs(DiaryEntryVO diary) {
        if (diary.getMedia() == null || diary.getMedia().isEmpty()) {
            return;
        }
        diary.getMedia().stream()
                .map(DiaryMediaVO::getGcsUrl)
                .filter(Objects::nonNull)
                .forEach(gcsStorageService::delete);
    }

    /**
     * 일기 변경이 발생한 월의 통계 캐시를 삭제한다.
     * 다음 통계 조회 시 최신 원본 데이터 기준으로 다시 계산된다.
     */
    private void invalidateMonthlyStat(Long userId, LocalDate writtenAt) {
        if (userId == null || writtenAt == null) {
            return;
        }
        monthlyStatRepository.deleteByUserIdAndRecordMonth(userId, YearMonth.from(writtenAt).toString());
    }

    /**
     * 사용자 입력(텍스트/이미지/음성)을 받아 AI 일기 초안을 생성한다.
     * <p>
     * 처리 순서:
     * 1. 입력값 유효성 검사 (하나 이상 필수)
     * 2. 이미지가 있으면 GCS에 업로드하여 URL 목록 확보
     * 3. Gemini로 일기 초안 및 감정 분석 생성
     * 4. 결과 반환 (DB 저장 없음 — 사용자 확인 후 {@code POST /diaries}로 최종 저장)
     *
     * @param command 사용자 ID, 텍스트/이미지/음성, 날짜, 모드를 담은 커맨드 객체
     * @return AI 생성 초안 텍스트, 감정 분석, 업로드된 미디어 URL 목록
     * @throws IllegalArgumentException 텍스트·이미지·음성이 모두 없는 경우
     */
    public DiaryDraftResponse createDraft(DiaryDraftCommand command) {
        validateInput(command);
        UserVO user = userRepository.findById(command.userId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + command.userId()));
        int imageCount = command.images() == null ? 0 : command.images().size();
        boolean hasText = command.textContent() != null && !command.textContent().isBlank();
        boolean hasVoice = command.voice() != null && !command.voice().isEmpty();
        log.info("AI draft start userId={} writtenAt={} hasText={} imageCount={} hasVoice={}",
                command.userId(), command.writtenAt(), hasText, imageCount, hasVoice);

        List<String> blobPaths = uploadImages(command.images());
        List<String> mediaUrls = blobPaths.stream()
                .map(gcsStorageService::generateSignedUrl)
                .toList();

        AiDraftResult aiResult = aiDraftGeneratorService.generate(
                command.textContent(),
                command.images(),
                command.voice(),
                command.writtenAt(),
                user.getGender(),
                user.getAgeGroup(),
                user.getAiDraftTone()
        );
        log.info("AI draft complete userId={} mediaCount={}",
                command.userId(), mediaUrls.size());

        return new DiaryDraftResponse(aiResult.generatedText(), toAiAnalysis(aiResult), mediaUrls);
    }

    /**
     * STT/BLIP 전처리 없이 텍스트·이미지·음성 원본을 Gemini 멀티모달 입력으로 전달해 초안을 생성한다.
     * v1과 동일하게 이미지 URL은 저장 확인 화면에서 사용할 수 있도록 GCS signed URL로 반환한다.
     */
    public DiaryDraftResponse createDraftV2(DiaryDraftCommand command) {
        validateInput(command);
        validateDraftV2Attachments(command.images(), command.voice());
        UserVO user = userRepository.findById(command.userId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + command.userId()));
        int imageCount = command.images() == null ? 0 : command.images().size();
        boolean hasText = command.textContent() != null && !command.textContent().isBlank();
        boolean hasVoice = command.voice() != null && !command.voice().isEmpty();
        long startedAt = System.currentTimeMillis();
        log.info("AI draft v2 start userId={} writtenAt={} hasText={} imageCount={} hasVoice={}",
                command.userId(), command.writtenAt(), hasText, imageCount, hasVoice);

        List<String> blobPaths = uploadImages(command.images());
        List<String> mediaUrls = blobPaths.stream()
                .map(gcsStorageService::generateSignedUrl)
                .toList();

        AiDraftResult aiResult = aiDraftGeneratorService.generateMultimodal(
                command.textContent(),
                command.images(),
                command.voice(),
                command.writtenAt(),
                user.getGender(),
                user.getAgeGroup(),
                user.getAiDraftTone()
        );
        log.info("AI draft v2 complete userId={} mediaCount={} elapsedMs={}",
                command.userId(), mediaUrls.size(), System.currentTimeMillis() - startedAt);

        return new DiaryDraftResponse(aiResult.generatedText(), toAiAnalysis(aiResult), mediaUrls);
    }

    /**
     * Gemini 응답 DTO를 저장 가능한 AI 분석 DTO로 변환한다.
     */
    private DiaryDraftResponse.AiAnalysis toAiAnalysis(AiDraftResult aiResult) {
        return new DiaryDraftResponse.AiAnalysis(
                aiResult.emotions(),
                aiResult.happinessIndex(),
                aiResult.activities(),
                aiResult.places(),
                aiResult.people(),
                aiResult.iabCategories(),
                aiResult.patterns(),
                aiResult.moodSummary(),
                aiResult.tone()
        );
    }

    /**
     * 텍스트·이미지·음성 중 하나 이상이 존재하는지 검사한다.
     * 공백만 있는 텍스트는 입력 없음으로 취급한다.
     */
    private void validateInput(DiaryDraftCommand command) {
        boolean hasText = command.textContent() != null && !command.textContent().isBlank();
        boolean hasImages = command.images() != null
                && command.images().stream().anyMatch(image -> image != null && !image.isEmpty());
        boolean hasVoice = command.voice() != null && !command.voice().isEmpty();

        if (!hasText && !hasImages && !hasVoice) {
            throw new IllegalArgumentException("텍스트, 이미지, 음성 중 하나 이상 입력해야 합니다.");
        }
    }

    private void validateDraftV2Attachments(List<MultipartFile> images, MultipartFile voice) {
        if (images != null) {
            List<MultipartFile> nonEmptyImages = images.stream()
                    .filter(image -> image != null && !image.isEmpty())
                    .toList();
            if (nonEmptyImages.size() > MAX_DRAFT_IMAGE_COUNT) {
                throw new IllegalArgumentException("이미지는 최대 4장까지 첨부할 수 있습니다.");
            }
            for (MultipartFile image : nonEmptyImages) {
                validateContentType(image, SUPPORTED_DRAFT_IMAGE_TYPES, "지원하지 않는 이미지 형식입니다.");
            }
        }
        if (voice != null && !voice.isEmpty()) {
            validateContentType(voice, SUPPORTED_DRAFT_AUDIO_TYPES, "지원하지 않는 음성 형식입니다.");
            validateVoiceDuration(voice);
        }
    }

    private void validateContentType(MultipartFile file, List<String> supportedTypes, String message) {
        String contentType = file.getContentType();
        if (contentType == null || !supportedTypes.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(message + " filename=" + file.getOriginalFilename());
        }
    }

    private void validateVoiceDuration(MultipartFile voice) {
        String filename = voice.getOriginalFilename() != null ? voice.getOriginalFilename() : "voice";
        String suffix = filename.contains(".") ? filename.substring(filename.lastIndexOf('.')) : ".tmp";
        File tempFile = null;
        try {
            tempFile = File.createTempFile("draft-v2-voice-", suffix);
            Files.write(tempFile.toPath(), voice.getBytes());
            long duration = new MultimediaObject(tempFile).getInfo().getDuration();
            if (duration > MAX_DRAFT_VOICE_DURATION_MS) {
                throw new IllegalArgumentException("음성 입력은 최대 60초까지 첨부할 수 있습니다.");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("음성 길이를 확인할 수 없습니다: " + filename, e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile.toPath());
                } catch (IOException e) {
                    log.warn("temporary voice file delete failed path={}", tempFile.getAbsolutePath(), e);
                }
            }
        }
    }

    /**
     * 이미지 파일 목록을 GCS에 업로드하고 URL 목록을 반환한다.
     * 이미지가 없으면 빈 리스트를 반환한다.
     *
     * @throws UncheckedIOException GCS 업로드 실패 시
     */
    private List<String> uploadImages(List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> urls = new ArrayList<>();
        for (MultipartFile image : images) {
            try {
                urls.add(gcsStorageService.upload(image, "uploads/images"));
            } catch (IOException e) {
                throw new UncheckedIOException("이미지 업로드 실패: " + image.getOriginalFilename(), e);
            }
        }
        return urls;
    }

    /**
     * 특정 연월의 일기 목록을 조회하여 반환한다.
     * <p>
     * 해당 월의 첫째 날부터 마지막 날까지 범위로 조회하며, 작성일 오름차순으로 정렬된다.
     *
     * @param userId JWT 인증 사용자 ID
     * @return 해당 월의 일기 목록 (작성일 오름차순)
     */
    @Transactional(readOnly = true)
    public List<DiaryResponse> getAllDiaries(Long userId){


        return diaryEntryRepository.findWithMediaByUser_Id(userId)
                .stream()
                .map(d -> DiaryResponse.fromSummary(d, gcsStorageService::generateSignedUrl))
                .toList();

    }
}
