package net.coboogie.diary.service;

import lombok.RequiredArgsConstructor;
import net.coboogie.diary.dto.AiDraftResult;
import net.coboogie.diary.dto.DiaryDraftCommand;
import net.coboogie.diary.dto.DiaryDraftResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 일기 도메인 핵심 비즈니스 로직 서비스.
 * <p>
 * 현재 구현: AI 초안 생성 ({@code POST /api/v1/diaries/draft})
 * 예정 구현: 최종 저장, 목록 조회, 별점 수정, 삭제
 */
@Service
@RequiredArgsConstructor
public class DiaryService {

    private final GcsStorageService gcsStorageService;
    private final AiDraftGeneratorService aiDraftGeneratorService;
    private final SpeechToTextService speechToTextService;

    /**
     * 사용자 입력(텍스트/이미지/음성)을 받아 AI 일기 초안을 생성한다.
     * <p>
     * 처리 순서:
     * 1. 입력값 유효성 검사 (하나 이상 필수)
     * 2. 이미지가 있으면 GCS에 업로드하여 URL 목록 확보
     * 3. OpenAI로 일기 초안 및 감정 분석 생성
     * 4. 결과 반환 (DB 저장 없음 — 사용자 확인 후 {@code POST /diaries}로 최종 저장)
     *
     * @param command 사용자 ID, 텍스트/이미지/음성, 날짜, 모드를 담은 커맨드 객체
     * @return AI 생성 초안 텍스트, 감정 분석, 업로드된 미디어 URL 목록
     * @throws IllegalArgumentException 텍스트·이미지·음성이 모두 없는 경우
     */
    public DiaryDraftResponse createDraft(DiaryDraftCommand command) {
        validateInput(command);

        List<String> mediaUrls = uploadImages(command.images());

        String voiceTranscription = transcribeVoice(command.voice());

        AiDraftResult aiResult = aiDraftGeneratorService.generate(
                command.textContent(),
                Collections.emptyList(),
                voiceTranscription,
                command.writtenAt()
        );

        return new DiaryDraftResponse(
                aiResult.generatedText(),
                new DiaryDraftResponse.AiAnalysis(
                        aiResult.emotionType(),
                        aiResult.emotionScore(),
                        aiResult.moodIndex(),
                        aiResult.detectedKeywords()
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
            return result.isBlank() ? null : result;
        } catch (IOException e) {
            throw new UncheckedIOException("음성 파일 전사 실패", e);
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
                urls.add(gcsStorageService.upload(image, "diary/images"));
            } catch (IOException e) {
                throw new UncheckedIOException("이미지 업로드 실패: " + image.getOriginalFilename(), e);
            }
        }
        return urls;
    }
}
