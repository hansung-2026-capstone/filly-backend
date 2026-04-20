package net.coboogie.diary.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coboogie.blip.services.ImageAnalysisService;
import net.coboogie.diary.dto.AiDraftResult;
import net.coboogie.diary.dto.DiaryDraftCommand;
import net.coboogie.diary.dto.DiaryDraftResponse;
import net.coboogie.diary.dto.DiarySaveCommand;
import net.coboogie.diary.dto.DiaryResponse;
import net.coboogie.diary.dto.DiaryUpdateRequest;
import net.coboogie.diary.repository.DiaryEntryRepository;
import net.coboogie.diary.repository.DiaryMediaRepository;
import net.coboogie.vo.DiaryEntryVO;
import net.coboogie.vo.DiaryMediaVO;
import net.coboogie.vo.UserVO;
import net.coboogie.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

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

    private final GcsStorageService gcsStorageService;
    private final AiDraftGeneratorService aiDraftGeneratorService;
    private final SpeechToTextService speechToTextService;
    private final ImageAnalysisService imageAnalysisService;
    private final UserRepository userRepository;
    private final DiaryEntryRepository diaryEntryRepository;
    private final DiaryMediaRepository diaryMediaRepository;

    /**
     * 일기를 DB에 저장하고 저장된 결과를 반환한다.
     * <p>
     * DEFAULT 모드: rawContent(텍스트)를 diary_entries에 저장한다.<br>
     * IMAGE/IMAGE_TEXT 모드: 이미지를 GCS에 업로드하고 diary_media에 저장한다.
     * 작성 날짜, 이모지, 모드를 함께 저장하며, 별점은 초기에 설정되지 않는다.
     *
     * @param command userId, rawContent, emoji, writtenAt, mode, images를 담은 커맨드 객체
     * @return 저장된 일기의 응답 DTO (mediaUrls 포함)
     * @throws IllegalArgumentException 존재하지 않는 userId인 경우
     * @throws UncheckedIOException     GCS 업로드 실패 시
     */
    @Transactional
    public DiaryResponse saveDiary(DiarySaveCommand command) {
        UserVO user = userRepository.findById(command.userId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + command.userId()));

        DiaryEntryVO diary = DiaryEntryVO.builder()
                .user(user)
                .rawContent(command.rawContent())
                .emoji(command.emoji())
                .writtenAt(command.writtenAt())
                .mode(command.mode())
                .build();

        DiaryEntryVO saved = diaryEntryRepository.save(diary);

        List<DiaryMediaVO> savedMedia = saveMediaFiles(saved, command.images());
        saved.setMedia(savedMedia);

        return DiaryResponse.from(saved);
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
        return DiaryResponse.from(diary);
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
                .findByUser_IdAndWrittenAtBetweenOrderByWrittenAtAsc(userId, startDate, endDate)
                .stream()
                .map(DiaryResponse::from)
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

        return DiaryResponse.from(diary);
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
        diaryEntryRepository.delete(diary);
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

        List<String> mediaUrls = uploadImages(command.images());
        List<String> imageCaptions = extractCaptions(command.images());

        String voiceTranscription = transcribeVoice(command.voice());

        AiDraftResult aiResult = aiDraftGeneratorService.generate(
                command.textContent(),
                imageCaptions,
                voiceTranscription,
                command.writtenAt()
        );

        return new DiaryDraftResponse(
                aiResult.generatedText(),
                new DiaryDraftResponse.AiAnalysis(
                        aiResult.emotions(),
                        aiResult.happinessIndex(),
                        aiResult.activities(),
                        aiResult.places(),
                        aiResult.people(),
                        aiResult.iabCategories(),
                        aiResult.patterns(),
                        aiResult.moodSummary(),
                        aiResult.tone()
                ),
                mediaUrls
        );
    }

    /**
     * 텍스트·이미지·음성 중 하나 이상이 존재하는지 검사한다.
     * 공백만 있는 텍스트는 입력 없음으로 취급한다.
     */
    private void validateInput(DiaryDraftCommand command) {
        boolean hasText = command.textContent() != null && !command.textContent().isBlank();
        boolean hasImages = command.images() != null && !command.images().isEmpty();
        boolean hasVoice = command.voice() != null;

        if (!hasText && !hasImages && !hasVoice) {
            throw new IllegalArgumentException("텍스트, 이미지, 음성 중 하나 이상 입력해야 합니다.");
        }
    }

    /**
     * 음성 파일이 있으면 Chirp STT로 전사하고 텍스트를 반환한다.
     * 음성이 없거나 전사 결과가 비어있으면 null을 반환한다.
     *
     * @throws UncheckedIOException STT 처리 실패 시
     */
    private String transcribeVoice(MultipartFile voice) {
        if (voice == null || voice.isEmpty()) {
            return null;
        }
        try {
            String result = speechToTextService.transcribe(voice);
            if (result.isBlank()) {
                log.warn("STT 전사 결과가 비어있습니다. filename={}", voice.getOriginalFilename());
                return null;
            }
            log.info("STT 전사 완료: {}", result);
            return result;
        } catch (Exception e) {
            log.error("STT 전사 실패. filename={}", voice.getOriginalFilename(), e);
            return null;
        }
    }

    /**
     * 이미지 파일 목록을 BLIP으로 분석하여 캡션 목록을 반환한다.
     * 이미지가 없으면 빈 리스트를 반환한다. 분석 실패한 이미지는 건너뛴다.
     */
    private List<String> extractCaptions(List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> captions = new ArrayList<>();
        for (MultipartFile image : images) {
            try {
                String caption = imageAnalysisService.analyzeCaption(image).caption();
                if (caption != null && !caption.isBlank()) {
                    captions.add(caption);
                }
            } catch (Exception e) {
                // BLIP 서버 미실행 등 분석 실패 시 해당 이미지 건너뜀
            }
        }
        return captions;
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
}
