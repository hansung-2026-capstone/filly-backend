package net.coboogie.diary.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coboogie.blip.services.ImageAnalysisService;
import net.coboogie.diary.dto.AiDraftResult;
import net.coboogie.diary.dto.DiaryDraftResponse;
import net.coboogie.diary.repository.AiEmotionAnalysisRepository;
import net.coboogie.diary.repository.DiaryEntryRepository;
import net.coboogie.vo.AiEmotionAnalysisVO;
import net.coboogie.vo.DiaryEntryVO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 저장 응답을 지연시키지 않도록 일기 AI 분석을 비동기로 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryAnalysisAsyncService {

    private final AiDraftGeneratorService aiDraftGeneratorService;
    private final ImageAnalysisService imageAnalysisService;
    private final DiaryEntryRepository diaryEntryRepository;
    private final AiEmotionAnalysisRepository aiEmotionAnalysisRepository;
    @Qualifier("aiObjectMapper")
    private final ObjectMapper objectMapper;

    /**
     * 비동기로 일기 감정 분석을 생성하고 저장한다.
     */
    @Async
    @Transactional
    public void analyzeAndSaveAsync(Long diaryId, String rawContent, LocalDate writtenAt,
                                    String gender, String ageGroup, String aiDraftTone,
                                    List<AnalysisImage> images) {
        long startedAt = System.currentTimeMillis();
        try {
            DiaryEntryVO diary = diaryEntryRepository.findById(diaryId)
                    .orElseThrow(() -> new IllegalArgumentException("일기를 찾을 수 없습니다: " + diaryId));
            List<String> imageCaptions = extractCaptions(images);
            AiDraftResult aiResult = aiDraftGeneratorService.generate(
                    rawContent,
                    imageCaptions,
                    null,
                    writtenAt,
                    gender,
                    ageGroup,
                    aiDraftTone
            );
            saveEmotionAnalysis(diary, toAiAnalysis(aiResult));
            log.info("일기 AI 분석 비동기 저장 완료: diaryId={} elapsedMs={}",
                    diaryId, System.currentTimeMillis() - startedAt);
        } catch (Exception e) {
            log.error("일기 AI 분석 비동기 저장 실패: diaryId={}", diaryId, e);
        }
    }

    private List<String> extractCaptions(List<AnalysisImage> images) {
        if (images == null || images.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> captions = new ArrayList<>();
        for (AnalysisImage image : images) {
            try {
                String caption = imageAnalysisService.analyzeCaption(new ByteArrayMultipartFile(image)).caption();
                if (caption != null && !caption.isBlank()) {
                    captions.add(caption);
                }
            } catch (Exception e) {
                log.warn("BLIP caption extraction skipped filename={} reason={}",
                        image.filename(), e.getMessage(), e);
            }
        }
        return captions;
    }

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

    public record AnalysisImage(byte[] bytes, String filename, String contentType) {
    }

    private static final class ByteArrayMultipartFile implements MultipartFile {

        private final AnalysisImage image;

        private ByteArrayMultipartFile(AnalysisImage image) {
            this.image = image;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return image.filename();
        }

        @Override
        public String getContentType() {
            return image.contentType();
        }

        @Override
        public boolean isEmpty() {
            return image.bytes().length == 0;
        }

        @Override
        public long getSize() {
            return image.bytes().length;
        }

        @Override
        public byte[] getBytes() {
            return image.bytes();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(image.bytes());
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            throw new UnsupportedOperationException("transferTo is not supported");
        }
    }
}
