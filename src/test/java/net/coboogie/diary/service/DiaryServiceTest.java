package net.coboogie.diary.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiaryServiceTest {

    @Mock private GcsStorageService gcsStorageService;
    @Mock private AiDraftGeneratorService aiDraftGeneratorService;
    @Mock private UserRepository userRepository;
    @Mock private DiaryEntryRepository diaryEntryRepository;
    @Mock private DiaryMediaRepository diaryMediaRepository;
    @Mock private AiEmotionAnalysisRepository aiEmotionAnalysisRepository;
    @Mock private AiDiaryResultRepository aiDiaryResultRepository;
    @Mock private MonthlyStatRepository monthlyStatRepository;
    @Mock private ArchiveDiaryRepository archiveDiaryRepository;
    @Mock private ArchiveEntryRepository archiveEntryRepository;
    @Mock private DiaryAnalysisAsyncService diaryAnalysisAsyncService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private DiaryService sut;

    private static final LocalDate WRITTEN_AT = LocalDate.of(2026, 4, 10);

    @Test
    @DisplayName("텍스트만 입력 시 GCS 업로드 없이 AI 초안 반환")
    void givenTextOnlyInput_whenCreateDraft_thenReturnDraftWithoutGcsUpload() {
        // given
        DiaryDraftCommand command = DiaryDraftCommand.builder()
                .userId(1L)
                .textContent("오늘은 날씨가 좋았다")
                .writtenAt(WRITTEN_AT)
                .build();

        AiDraftResult aiResult = new AiDraftResult(
                "오늘 따뜻한 햇살이 기분을 밝게 해주었다.",
                List.of(new AiDraftResult.EmotionScore("기쁨", 0.8f)),
                75,
                List.of("산책"),
                List.of("공원"),
                List.of(),
                List.of("라이프스타일>자기계발"),
                new AiDraftResult.Patterns("오후", 7, "혼자", false, null, "맑음", "좋음", "언급없음"),
                "따뜻했던 하루",
                "실시간"
        );
        UserVO mockUser = UserVO.builder()
                .id(1L)
                .oauthProvider("google")
                .oauthId("abc")
                .gender("female")
                .ageGroup("20대")
                .aiDraftTone("warm")
                .build();
        given(userRepository.findById(1L)).willReturn(Optional.of(mockUser));
        given(aiDraftGeneratorService.generate(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(aiResult);

        // when
        DiaryDraftResponse response = sut.createDraft(command);

        // then
        assertThat(response.generatedText()).isEqualTo("오늘 따뜻한 햇살이 기분을 밝게 해주었다.");
        assertThat(response.aiAnalysis().emotions()).hasSize(1);
        assertThat(response.aiAnalysis().happinessIndex()).isEqualTo(75);
        assertThat(response.mediaUrls()).isEmpty();
        verifyNoInteractions(gcsStorageService);
    }

    @Test
    @DisplayName("이미지 입력 시 GCS 업로드 후 mediaUrls 포함하여 초안 반환")
    void givenImagesProvided_whenCreateDraft_thenUploadToGcsAndIncludeMediaUrls() throws IOException {
        // given
        MultipartFile mockImage = mock(MultipartFile.class);
        DiaryDraftCommand command = DiaryDraftCommand.builder()
                .userId(1L)
                .images(List.of(mockImage))
                .writtenAt(WRITTEN_AT)
                .build();

        String blobPath = "uploads/images/uuid_photo.jpg";
        String signedUrl = "https://storage.googleapis.com/filly-media-bucket/" + blobPath + "?X-Goog-Signature=abc";
        AiDraftResult aiResult = new AiDraftResult(
                "이미지 속 풍경이 아름다웠다.",
                List.of(new AiDraftResult.EmotionScore("평온", 0.7f)),
                60,
                List.of(), List.of(), List.of(), List.of(),
                new AiDraftResult.Patterns("오후", 5, "혼자", false, null, "없음", "보통", "언급없음"),
                "잔잔한 하루", "실시간"
        );

        given(gcsStorageService.upload(mockImage, "uploads/images")).willReturn(blobPath);
        given(gcsStorageService.generateSignedUrl(blobPath)).willReturn(signedUrl);
        UserVO mockUser = UserVO.builder().id(1L).oauthProvider("google").oauthId("abc").build();
        given(userRepository.findById(1L)).willReturn(Optional.of(mockUser));
        given(aiDraftGeneratorService.generate(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(aiResult);

        // when
        DiaryDraftResponse response = sut.createDraft(command);

        // then
        assertThat(response.mediaUrls()).containsExactly(signedUrl);
        verify(gcsStorageService).upload(mockImage, "uploads/images");
        verify(gcsStorageService).generateSignedUrl(blobPath);
    }

    @Test
    @DisplayName("v2 텍스트만 입력 시 STT와 BLIP 없이 멀티모달 AI 초안 반환")
    void givenTextOnlyInput_whenCreateDraftV2_thenUseMultimodalGeneratorWithoutPreprocessing() {
        // given
        DiaryDraftCommand command = DiaryDraftCommand.builder()
                .userId(1L)
                .textContent("오늘은 날씨가 좋았다")
                .writtenAt(WRITTEN_AT)
                .build();

        AiDraftResult aiResult = new AiDraftResult(
                "오늘은 날씨가 좋아 마음도 가벼웠다.",
                List.of(new AiDraftResult.EmotionScore("기쁨", 0.8f)),
                75,
                List.of("산책"),
                List.of(),
                List.of(),
                List.of(),
                new AiDraftResult.Patterns("오후", 7, "혼자", false, null, "맑음", "좋음", "언급없음"),
                "가벼운 하루",
                "실시간"
        );
        UserVO mockUser = UserVO.builder()
                .id(1L)
                .oauthProvider("google")
                .oauthId("abc")
                .gender("female")
                .ageGroup("20대")
                .aiDraftTone("warm")
                .build();
        given(userRepository.findById(1L)).willReturn(Optional.of(mockUser));
        given(aiDraftGeneratorService.generateMultimodal(anyString(), any(), any(), any(), any(), any(), any()))
                .willReturn(aiResult);

        // when
        DiaryDraftResponse response = sut.createDraftV2(command);

        // then
        assertThat(response.generatedText()).isEqualTo("오늘은 날씨가 좋아 마음도 가벼웠다.");
        assertThat(response.mediaUrls()).isEmpty();
        verify(aiDraftGeneratorService).generateMultimodal(
                eq("오늘은 날씨가 좋았다"), isNull(), isNull(), eq(WRITTEN_AT),
                eq("female"), eq("20대"), eq("warm"));
        verifyNoInteractions(gcsStorageService);
    }

    @Test
    @DisplayName("v2 이미지 입력 시 BLIP 없이 GCS 업로드 URL과 멀티모달 AI 초안 반환")
    void givenImagesProvided_whenCreateDraftV2_thenUploadAndUseOriginalImages() throws IOException {
        // given
        MockMultipartFile image = new MockMultipartFile(
                "images", "photo.jpg", "image/jpeg", "fake-image".getBytes());
        DiaryDraftCommand command = DiaryDraftCommand.builder()
                .userId(1L)
                .images(List.of(image))
                .writtenAt(WRITTEN_AT)
                .build();
        String blobPath = "uploads/images/uuid_photo.jpg";
        String signedUrl = "https://storage.googleapis.com/filly-media-bucket/" + blobPath;
        AiDraftResult aiResult = new AiDraftResult(
                "사진 속 순간을 떠올리며 하루를 정리했다.",
                List.of(), 60, List.of(), List.of(), List.of(), List.of(),
                new AiDraftResult.Patterns("오후", 5, "혼자", false, null, "없음", "보통", "언급없음"),
                "사진으로 남긴 하루", "실시간"
        );
        UserVO mockUser = UserVO.builder().id(1L).oauthProvider("google").oauthId("abc").build();
        given(userRepository.findById(1L)).willReturn(Optional.of(mockUser));
        given(gcsStorageService.upload(image, "uploads/images")).willReturn(blobPath);
        given(gcsStorageService.generateSignedUrl(blobPath)).willReturn(signedUrl);
        given(aiDraftGeneratorService.generateMultimodal(any(), anyList(), any(), any(), any(), any(), any()))
                .willReturn(aiResult);

        // when
        DiaryDraftResponse response = sut.createDraftV2(command);

        // then
        assertThat(response.mediaUrls()).containsExactly(signedUrl);
        verify(aiDraftGeneratorService).generateMultimodal(
                isNull(), eq(List.of(image)), isNull(), eq(WRITTEN_AT), any(), any(), any());
    }

    @Test
    @DisplayName("v2 이미지는 최대 4장까지만 허용한다")
    void givenTooManyImages_whenCreateDraftV2_thenThrowIllegalArgumentException() {
        // given
        List<MultipartFile> images = List.of(
                new MockMultipartFile("images", "1.jpg", "image/jpeg", "1".getBytes()),
                new MockMultipartFile("images", "2.jpg", "image/jpeg", "2".getBytes()),
                new MockMultipartFile("images", "3.jpg", "image/jpeg", "3".getBytes()),
                new MockMultipartFile("images", "4.jpg", "image/jpeg", "4".getBytes()),
                new MockMultipartFile("images", "5.jpg", "image/jpeg", "5".getBytes())
        );
        DiaryDraftCommand command = DiaryDraftCommand.builder()
                .userId(1L)
                .images(images)
                .writtenAt(WRITTEN_AT)
                .build();

        // when & then
        assertThatThrownBy(() -> sut.createDraftV2(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("최대 4장");
        verifyNoInteractions(userRepository, gcsStorageService, aiDraftGeneratorService);
    }

    @Test
    @DisplayName("텍스트, 이미지, 음성 모두 없으면 IllegalArgumentException 발생")
    void givenNoInput_whenCreateDraft_thenThrowIllegalArgumentException() {
        // given
        DiaryDraftCommand command = DiaryDraftCommand.builder()
                .userId(1L)
                .writtenAt(WRITTEN_AT)
                .build();

        // when & then
        assertThatThrownBy(() -> sut.createDraft(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("텍스트, 이미지, 음성");

        verifyNoInteractions(gcsStorageService, aiDraftGeneratorService);
    }

    @Test
    @DisplayName("공백만 있는 텍스트는 빈 입력으로 취급하여 IllegalArgumentException 발생")
    void givenBlankTextOnly_whenCreateDraft_thenThrowIllegalArgumentException() {
        // given
        DiaryDraftCommand command = DiaryDraftCommand.builder()
                .userId(1L)
                .textContent("   ")
                .writtenAt(WRITTEN_AT)
                .build();

        // when & then
        assertThatThrownBy(() -> sut.createDraft(command))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─────────────────────────────────────────────────────
    // saveDiary 테스트
    // ─────────────────────────────────────────────────────

    @Test
    @DisplayName("일기 저장 시 diary_entries에 저장 후 DiaryResponse 반환")
    void givenTextCommand_whenSaveDiary_thenSaveAndReturnResponse() throws JsonProcessingException {
        // given
        Long userId = 1L;
        UserVO mockUser = UserVO.builder().id(userId).oauthProvider("google").oauthId("abc").build();

        DiarySaveCommand command = DiarySaveCommand.builder()
                .userId(userId)
                .rawContent("오늘 날씨가 맑았다.")
                .emoji("☀️")
                .writtenAt(WRITTEN_AT)
                .build();

        DiaryEntryVO savedDiary = DiaryEntryVO.builder()
                .id(10L)
                .user(mockUser)
                .rawContent("오늘 날씨가 맑았다.")
                .emoji("☀️")
                .writtenAt(WRITTEN_AT)
                .createdAt(LocalDateTime.now())
                .build();
        given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));
        given(diaryEntryRepository.save(any(DiaryEntryVO.class))).willReturn(savedDiary);

        // when
        DiaryResponse response = sut.saveDiary(command);

        // then
        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.rawContent()).isEqualTo("오늘 날씨가 맑았다.");
        assertThat(response.emoji()).isEqualTo("☀️");
        assertThat(response.writtenAt()).isEqualTo(WRITTEN_AT);
        verify(diaryEntryRepository).save(any(DiaryEntryVO.class));
        verify(diaryAnalysisAsyncService).analyzeAndSaveAsync(
                eq(10L),
                eq("오늘 날씨가 맑았다."),
                eq(WRITTEN_AT),
                eq("none"),
                eq("none"),
                eq("none"),
                eq(Collections.emptyList()));
        verify(aiDraftGeneratorService, never()).generate(any(), anyList(), any(), any(), any(), any(), any());
        verify(aiEmotionAnalysisRepository, never()).save(any(AiEmotionAnalysisVO.class));
        verify(aiDiaryResultRepository, never()).save(any());
        verify(monthlyStatRepository).deleteByUserIdAndRecordMonth(userId, "2026-04");
    }

    @Test
    @DisplayName("이미지 저장 시 GCS 업로드 후 mediaUrls 포함하여 DiaryResponse 반환")
    void givenImageCommand_whenSaveDiary_thenUploadToGcsAndReturnResponseWithMediaUrls()
            throws IOException, JsonProcessingException {
        // given
        Long userId = 1L;
        UserVO mockUser = UserVO.builder().id(userId).oauthProvider("google").oauthId("abc").build();
        MultipartFile mockImage = mock(MultipartFile.class);
        given(mockImage.getSize()).willReturn(1024L);
        DiaryDraftResponse.AiAnalysis aiAnalysis = new DiaryDraftResponse.AiAnalysis(
                List.of(new AiDraftResult.EmotionScore("평온", 0.7f)),
                60,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new AiDraftResult.Patterns("오후", 5, "혼자", false, null, "없음", "보통", "언급없음"),
                "차분한 하루",
                "담담함"
        );

        DiarySaveCommand command = DiarySaveCommand.builder()
                .userId(userId)
                .emoji("📷")
                .writtenAt(WRITTEN_AT)
                .images(List.of(mockImage))
                .aiAnalysis(aiAnalysis)
                .build();

        String blobPath = "uploads/images/photo.jpg";
        String signedUrl = "https://storage.googleapis.com/filly-media-bucket/" + blobPath + "?X-Goog-Signature=abc";
        DiaryEntryVO savedDiary = DiaryEntryVO.builder()
                .id(10L).user(mockUser).emoji("📷").writtenAt(WRITTEN_AT)
                .createdAt(LocalDateTime.now()).build();
        DiaryMediaVO savedMedia = DiaryMediaVO.builder()
                .id(1L).diary(savedDiary).type(DiaryMediaVO.Type.IMAGE)
                .gcsUrl(blobPath).fileSize(1024).build();

        given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));
        given(diaryEntryRepository.save(any(DiaryEntryVO.class))).willReturn(savedDiary);
        given(gcsStorageService.upload(mockImage, "uploads/images")).willReturn(blobPath);
        given(gcsStorageService.generateSignedUrl(blobPath)).willReturn(signedUrl);
        given(diaryMediaRepository.save(any(DiaryMediaVO.class))).willReturn(savedMedia);
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        // when
        DiaryResponse response = sut.saveDiary(command);

        // then
        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.mediaUrls()).containsExactly(signedUrl);
        verify(gcsStorageService).upload(mockImage, "uploads/images");
        verify(gcsStorageService).generateSignedUrl(blobPath);
        verify(diaryMediaRepository).save(any(DiaryMediaVO.class));
        verify(aiDraftGeneratorService, never()).generate(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 userId로 저장 시 IllegalArgumentException 발생")
    void givenNonExistentUserId_whenSaveDiary_thenThrowIllegalArgumentException() {
        // given
        DiarySaveCommand command = DiarySaveCommand.builder()
                .userId(999L)
                .rawContent("테스트")
                .writtenAt(WRITTEN_AT)
                .build();

        given(userRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> sut.saveDiary(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");

        verifyNoInteractions(diaryEntryRepository);
    }

    @Test
    @DisplayName("본문과 이미지가 모두 없으면 저장 시 IllegalArgumentException 발생")
    void givenNoContentAndNoImages_whenSaveDiary_thenThrowIllegalArgumentException() {
        // given
        Long userId = 1L;
        UserVO mockUser = UserVO.builder().id(userId).oauthProvider("google").oauthId("abc").build();
        DiarySaveCommand command = DiarySaveCommand.builder()
                .userId(userId)
                .rawContent("   ")
                .emoji("😊")
                .writtenAt(WRITTEN_AT)
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));

        // when & then
        assertThatThrownBy(() -> sut.saveDiary(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rawContent 또는 images");

        verifyNoInteractions(diaryEntryRepository, aiDraftGeneratorService,
                aiEmotionAnalysisRepository, diaryAnalysisAsyncService);
    }

    @Test
    @DisplayName("aiAnalysis 포함 시 ai_diary_analysis 저장")
    void givenAiAnalysis_whenSaveDiary_thenSaveEmotionAnalysis() throws JsonProcessingException {
        // given
        Long userId = 1L;
        UserVO mockUser = UserVO.builder().id(userId).oauthProvider("google").oauthId("abc").build();
        DiaryEntryVO savedDiary = DiaryEntryVO.builder()
                .id(10L).user(mockUser).rawContent("내용")
                .writtenAt(WRITTEN_AT)
                .createdAt(LocalDateTime.now()).build();

        DiaryDraftResponse.AiAnalysis aiAnalysis = new DiaryDraftResponse.AiAnalysis(
                List.of(new AiDraftResult.EmotionScore("기쁨", 0.8f)),
                75,
                List.of("산책"),
                List.of("공원"),
                List.of(),
                List.of("라이프스타일"),
                new AiDraftResult.Patterns("오후", 7, "혼자", false, null, "맑음", "좋음", "언급없음"),
                "따뜻한 하루",
                "실시간"
        );

        DiarySaveCommand command = DiarySaveCommand.builder()
                .userId(userId).rawContent("내용").emoji("😊").writtenAt(WRITTEN_AT)
                .aiAnalysis(aiAnalysis).build();

        given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));
        given(diaryEntryRepository.save(any(DiaryEntryVO.class))).willReturn(savedDiary);
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        // when
        sut.saveDiary(command);

        // then
        verify(aiEmotionAnalysisRepository).save(any(AiEmotionAnalysisVO.class));
        verify(aiDiaryResultRepository, never()).save(any());
    }

    @Test
    @DisplayName("generatedText 포함 시 ai_diary_results 저장")
    void givenGeneratedText_whenSaveDiary_thenSaveAiDiaryResult() throws JsonProcessingException {
        // given
        Long userId = 1L;
        UserVO mockUser = UserVO.builder().id(userId).oauthProvider("google").oauthId("abc").build();
        DiaryEntryVO savedDiary = DiaryEntryVO.builder()
                .id(10L).user(mockUser).rawContent("내용")
                .writtenAt(WRITTEN_AT)
                .createdAt(LocalDateTime.now()).build();

        DiaryDraftResponse.AiAnalysis aiAnalysis = new DiaryDraftResponse.AiAnalysis(
                List.of(new AiDraftResult.EmotionScore("기쁨", 0.8f)),
                75,
                List.of("산책"),
                List.of("공원"),
                List.of(),
                List.of("라이프스타일"),
                new AiDraftResult.Patterns("오후", 7, "혼자", false, null, "맑음", "좋음", "언급없음"),
                "즐거운 하루",
                "따뜻함"
        );

        DiarySaveCommand command = DiarySaveCommand.builder()
                .userId(userId).rawContent("내용").emoji("😊").writtenAt(WRITTEN_AT)
                .aiAnalysis(aiAnalysis)
                .generatedText("AI가 작성한 일기").build();

        given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));
        given(diaryEntryRepository.save(any(DiaryEntryVO.class))).willReturn(savedDiary);
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        // when
        sut.saveDiary(command);

        // then
        verify(aiDiaryResultRepository).save(any(AiDiaryResultVO.class));
        verify(aiEmotionAnalysisRepository).save(any(AiEmotionAnalysisVO.class));
        verify(aiDraftGeneratorService, never()).generate(any(), any(), any(), any(), any(), any(), any());
    }

    // ─────────────────────────────────────────────────────
    // getDiary 테스트
    // ─────────────────────────────────────────────────────

    @Test
    @DisplayName("본인 소유 일기 조회 시 DiaryResponse 반환")
    void givenValidDiaryId_whenGetDiary_thenReturnDiaryResponse() {
        // given
        Long userId = 1L;
        Long diaryId = 10L;
        UserVO mockUser = UserVO.builder().id(userId).oauthProvider("google").oauthId("abc").build();
        DiaryEntryVO diary = DiaryEntryVO.builder()
                .id(diaryId)
                .user(mockUser)
                .rawContent("오늘은 즐거운 하루였다.")
                .emoji("😊")
                .writtenAt(WRITTEN_AT)
                .createdAt(LocalDateTime.now())
                .build();

        given(diaryEntryRepository.findByIdAndUser_Id(diaryId, userId)).willReturn(Optional.of(diary));

        // when
        DiaryResponse response = sut.getDiary(diaryId, userId);

        // then
        assertThat(response.id()).isEqualTo(diaryId);
        assertThat(response.rawContent()).isEqualTo("오늘은 즐거운 하루였다.");
        assertThat(response.emoji()).isEqualTo("😊");
        assertThat(response.writtenAt()).isEqualTo(WRITTEN_AT);
    }

    @Test
    @DisplayName("존재하지 않거나 타인 소유 일기 조회 시 NoSuchElementException 발생")
    void givenNonExistentDiaryId_whenGetDiary_thenThrowNoSuchElementException() {
        // given
        Long userId = 1L;
        Long diaryId = 999L;

        given(diaryEntryRepository.findByIdAndUser_Id(diaryId, userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> sut.getDiary(diaryId, userId))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("일기를 찾을 수 없습니다");
    }

    // ─────────────────────────────────────────────────────
    // getDiariesByMonth 테스트
    // ─────────────────────────────────────────────────────

    @Test
    @DisplayName("해당 월에 일기가 있으면 작성일 오름차순 목록 반환")
    void givenDiariesInMonth_whenGetDiariesByMonth_thenReturnSortedList() {
        // given
        Long userId = 1L;
        UserVO mockUser = UserVO.builder().id(userId).oauthProvider("google").oauthId("abc").build();

        DiaryEntryVO diary1 = DiaryEntryVO.builder()
                .id(1L).user(mockUser).rawContent("첫째 날").writtenAt(LocalDate.of(2026, 4, 1))
                .createdAt(LocalDateTime.now()).build();
        DiaryEntryVO diary2 = DiaryEntryVO.builder()
                .id(2L).user(mockUser).rawContent("셋째 날").writtenAt(LocalDate.of(2026, 4, 3))
                .createdAt(LocalDateTime.now()).build();

        given(diaryEntryRepository.findWithMediaByUser_IdAndWrittenAtBetweenOrderByWrittenAtAsc(
                userId, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)))
                .willReturn(List.of(diary1, diary2));

        // when
        List<DiaryResponse> result = sut.getDiariesByMonth(userId, 2026, 4);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).writtenAt()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(result.get(1).writtenAt()).isEqualTo(LocalDate.of(2026, 4, 3));
    }

    @Test
    @DisplayName("월별 일기 목록 조회 시 미디어 URL을 signed URL로 변환한다")
    void givenDiariesWithMedia_whenGetDiariesByMonth_thenReturnSignedMediaUrls() {
        // given
        Long userId = 1L;
        UserVO mockUser = UserVO.builder().id(userId).oauthProvider("google").oauthId("abc").build();
        DiaryEntryVO diary = DiaryEntryVO.builder()
                .id(1L).user(mockUser).rawContent("이미지 일기").writtenAt(LocalDate.of(2026, 4, 1))
                .createdAt(LocalDateTime.now()).build();
        DiaryMediaVO media = DiaryMediaVO.builder()
                .id(10L)
                .diary(diary)
                .type(DiaryMediaVO.Type.IMAGE)
                .gcsUrl("uploads/images/photo.jpg")
                .fileSize(1024)
                .build();
        diary.setMedia(List.of(media));

        given(diaryEntryRepository.findWithMediaByUser_IdAndWrittenAtBetweenOrderByWrittenAtAsc(
                userId, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)))
                .willReturn(List.of(diary));
        given(gcsStorageService.generateSignedUrl("uploads/images/photo.jpg"))
                .willReturn("https://storage.googleapis.com/bucket/uploads/images/photo.jpg?sig=abc");

        // when
        List<DiaryResponse> result = sut.getDiariesByMonth(userId, 2026, 4);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).mediaUrls())
                .containsExactly("https://storage.googleapis.com/bucket/uploads/images/photo.jpg?sig=abc");
    }

    @Test
    @DisplayName("해당 월에 일기가 없으면 빈 목록 반환")
    void givenNoDiariesInMonth_whenGetDiariesByMonth_thenReturnEmptyList() {
        // given
        Long userId = 1L;

        given(diaryEntryRepository.findWithMediaByUser_IdAndWrittenAtBetweenOrderByWrittenAtAsc(
                userId, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)))
                .willReturn(Collections.emptyList());

        // when
        List<DiaryResponse> result = sut.getDiariesByMonth(userId, 2026, 4);

        // then
        assertThat(result).isEmpty();
    }

    // ─────────────────────────────────────────────────────
    // updateDiary 테스트
    // ─────────────────────────────────────────────────────

    @Test
    @DisplayName("rawContent와 emoji 모두 수정 시 변경된 값으로 DiaryResponse 반환")
    void givenUpdateRequest_whenUpdateDiary_thenReturnUpdatedResponse() {
        // given
        Long userId = 1L;
        Long diaryId = 10L;
        UserVO mockUser = UserVO.builder().id(userId).oauthProvider("google").oauthId("abc").build();
        DiaryEntryVO diary = DiaryEntryVO.builder()
                .id(diaryId).user(mockUser)
                .rawContent("기존 내용").emoji("😐")
                .writtenAt(WRITTEN_AT)
                .createdAt(LocalDateTime.now()).build();

        DiaryUpdateRequest request = new DiaryUpdateRequest("수정된 내용", "😊");
        given(diaryEntryRepository.findByIdAndUser_Id(diaryId, userId)).willReturn(Optional.of(diary));

        // when
        DiaryResponse response = sut.updateDiary(diaryId, userId, request);

        // then
        assertThat(response.rawContent()).isEqualTo("수정된 내용");
        assertThat(response.emoji()).isEqualTo("😊");
        assertThat(response.updatedAt()).isNotNull();
        verify(monthlyStatRepository).deleteByUserIdAndRecordMonth(userId, "2026-04");
    }

    @Test
    @DisplayName("rawContent만 수정 시 emoji는 기존 값 유지")
    void givenRawContentOnly_whenUpdateDiary_thenEmojiUnchanged() {
        // given
        Long userId = 1L;
        Long diaryId = 10L;
        UserVO mockUser = UserVO.builder().id(userId).oauthProvider("google").oauthId("abc").build();
        DiaryEntryVO diary = DiaryEntryVO.builder()
                .id(diaryId).user(mockUser)
                .rawContent("기존 내용").emoji("😐")
                .writtenAt(WRITTEN_AT)
                .createdAt(LocalDateTime.now()).build();

        DiaryUpdateRequest request = new DiaryUpdateRequest("수정된 내용", null);
        given(diaryEntryRepository.findByIdAndUser_Id(diaryId, userId)).willReturn(Optional.of(diary));

        // when
        DiaryResponse response = sut.updateDiary(diaryId, userId, request);

        // then
        assertThat(response.rawContent()).isEqualTo("수정된 내용");
        assertThat(response.emoji()).isEqualTo("😐");
    }

    @Test
    @DisplayName("존재하지 않는 일기 수정 시 NoSuchElementException 발생")
    void givenNonExistentDiary_whenUpdateDiary_thenThrowNoSuchElementException() {
        // given
        Long userId = 1L;
        Long diaryId = 999L;
        DiaryUpdateRequest request = new DiaryUpdateRequest("내용", "😊");

        given(diaryEntryRepository.findByIdAndUser_Id(diaryId, userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> sut.updateDiary(diaryId, userId, request))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("일기를 찾을 수 없습니다");
    }

    // ─────────────────────────────────────────────────────
    // diary media 테스트
    // ─────────────────────────────────────────────────────

    @Test
    @DisplayName("일기 이미지 추가 시 GCS 업로드 후 미디어 응답에 id와 url 포함")
    void givenImages_whenAddDiaryMedia_thenUploadAndReturnMedia() throws IOException {
        // given
        Long userId = 1L;
        Long diaryId = 10L;
        UserVO mockUser = UserVO.builder().id(userId).oauthProvider("google").oauthId("abc").build();
        DiaryEntryVO diary = DiaryEntryVO.builder()
                .id(diaryId).user(mockUser).rawContent("내용").emoji("😊")
                .writtenAt(WRITTEN_AT).media(new ArrayList<>()).build();
        MultipartFile image = mock(MultipartFile.class);
        given(image.isEmpty()).willReturn(false);
        given(image.getSize()).willReturn(2048L);
        String blobPath = "uploads/images/photo.jpg";
        String signedUrl = "https://storage.googleapis.com/bucket/uploads/images/photo.jpg?sig=abc";
        DiaryMediaVO savedMedia = DiaryMediaVO.builder()
                .id(3L).diary(diary).type(DiaryMediaVO.Type.IMAGE).gcsUrl(blobPath).fileSize(2048).build();

        given(diaryEntryRepository.findByIdAndUser_Id(diaryId, userId)).willReturn(Optional.of(diary));
        given(gcsStorageService.upload(image, "uploads/images")).willReturn(blobPath);
        given(diaryMediaRepository.save(any(DiaryMediaVO.class))).willReturn(savedMedia);
        given(gcsStorageService.generateSignedUrl(blobPath)).willReturn(signedUrl);

        // when
        DiaryResponse response = sut.addDiaryMedia(diaryId, userId, List.of(image));

        // then
        assertThat(response.media()).hasSize(1);
        assertThat(response.media().get(0).id()).isEqualTo(3L);
        assertThat(response.mediaUrls()).containsExactly(signedUrl);
        verify(monthlyStatRepository).deleteByUserIdAndRecordMonth(userId, "2026-04");
    }

    @Test
    @DisplayName("일기 이미지 교체 시 새 파일 업로드 후 기존 GCS 객체 삭제")
    void givenImage_whenReplaceDiaryMedia_thenUploadNewAndDeleteOld() throws IOException {
        // given
        Long userId = 1L;
        Long diaryId = 10L;
        Long mediaId = 3L;
        UserVO mockUser = UserVO.builder().id(userId).oauthProvider("google").oauthId("abc").build();
        DiaryEntryVO diary = DiaryEntryVO.builder()
                .id(diaryId).user(mockUser).rawContent("내용").emoji("😊").writtenAt(WRITTEN_AT).build();
        DiaryMediaVO media = DiaryMediaVO.builder()
                .id(mediaId).diary(diary).type(DiaryMediaVO.Type.IMAGE)
                .gcsUrl("uploads/images/old.jpg").fileSize(100).build();
        diary.setMedia(List.of(media));
        MultipartFile image = mock(MultipartFile.class);
        given(image.isEmpty()).willReturn(false);
        given(image.getSize()).willReturn(300L);

        given(diaryEntryRepository.findByIdAndUser_Id(diaryId, userId)).willReturn(Optional.of(diary));
        given(diaryMediaRepository.findByIdAndDiary_IdAndDiary_User_Id(mediaId, diaryId, userId))
                .willReturn(Optional.of(media));
        given(gcsStorageService.upload(image, "uploads/images")).willReturn("uploads/images/new.jpg");
        given(gcsStorageService.generateSignedUrl("uploads/images/new.jpg")).willReturn("signed-new");

        // when
        DiaryResponse response = sut.replaceDiaryMedia(diaryId, userId, mediaId, image);

        // then
        assertThat(response.media().get(0).url()).isEqualTo("signed-new");
        verify(gcsStorageService).delete("uploads/images/old.jpg");
        verify(monthlyStatRepository).deleteByUserIdAndRecordMonth(userId, "2026-04");
    }

    @Test
    @DisplayName("일기 이미지 삭제 시 DB와 GCS에서 삭제")
    void givenMediaId_whenDeleteDiaryMedia_thenDeleteMediaAndBlob() {
        // given
        Long userId = 1L;
        Long diaryId = 10L;
        Long mediaId = 3L;
        UserVO mockUser = UserVO.builder().id(userId).oauthProvider("google").oauthId("abc").build();
        DiaryMediaVO media = DiaryMediaVO.builder()
                .id(mediaId).type(DiaryMediaVO.Type.IMAGE).gcsUrl("uploads/images/photo.jpg").build();
        DiaryEntryVO diary = DiaryEntryVO.builder()
                .id(diaryId).user(mockUser).rawContent("내용").emoji("😊")
                .writtenAt(WRITTEN_AT).media(new ArrayList<>(List.of(media))).build();
        media.setDiary(diary);

        given(diaryEntryRepository.findByIdAndUser_Id(diaryId, userId)).willReturn(Optional.of(diary));
        given(diaryMediaRepository.findByIdAndDiary_IdAndDiary_User_Id(mediaId, diaryId, userId))
                .willReturn(Optional.of(media));

        // when
        DiaryResponse response = sut.deleteDiaryMedia(diaryId, userId, mediaId);

        // then
        assertThat(response.media()).isEmpty();
        verify(diaryMediaRepository).delete(media);
        verify(gcsStorageService).delete("uploads/images/photo.jpg");
        verify(monthlyStatRepository).deleteByUserIdAndRecordMonth(userId, "2026-04");
    }

    // ─────────────────────────────────────────────────────
    // deleteDiary 테스트
    // ─────────────────────────────────────────────────────

    @Test
    @DisplayName("본인 소유 일기 삭제 시 delete 호출")
    void givenValidDiary_whenDeleteDiary_thenDeleteCalled() {
        // given
        Long userId = 1L;
        Long diaryId = 10L;
        UserVO mockUser = UserVO.builder().id(userId).oauthProvider("google").oauthId("abc").build();
        DiaryMediaVO media = DiaryMediaVO.builder()
                .id(3L).type(DiaryMediaVO.Type.IMAGE).gcsUrl("uploads/images/photo.jpg").build();
        DiaryEntryVO diary = DiaryEntryVO.builder()
                .id(diaryId).user(mockUser).rawContent("내용")
                .writtenAt(WRITTEN_AT)
                .media(List.of(media))
                .createdAt(LocalDateTime.now()).build();
        media.setDiary(diary);

        given(diaryEntryRepository.findByIdAndUser_Id(diaryId, userId)).willReturn(Optional.of(diary));

        // when
        sut.deleteDiary(diaryId, userId);

        // then
        verify(aiEmotionAnalysisRepository).deleteByDiaryId(diaryId);
        verify(aiDiaryResultRepository).deleteByDiaryId(diaryId);
        verify(archiveDiaryRepository).deleteByDiaryId(diaryId);
        verify(archiveEntryRepository).deleteByDiaryId(diaryId);
        verify(diaryEntryRepository).delete(diary);
        verify(gcsStorageService).delete("uploads/images/photo.jpg");
        verify(monthlyStatRepository).deleteByUserIdAndRecordMonth(userId, "2026-04");
    }

    @Test
    @DisplayName("존재하지 않는 일기 삭제 시 NoSuchElementException 발생")
    void givenNonExistentDiary_whenDeleteDiary_thenThrowNoSuchElementException() {
        // given
        Long userId = 1L;
        Long diaryId = 999L;

        given(diaryEntryRepository.findByIdAndUser_Id(diaryId, userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> sut.deleteDiary(diaryId, userId))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("일기를 찾을 수 없습니다");

        verify(diaryEntryRepository, never()).delete(any());
        verifyNoInteractions(monthlyStatRepository);
    }
}
