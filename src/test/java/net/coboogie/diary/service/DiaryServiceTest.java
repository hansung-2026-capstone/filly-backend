package net.coboogie.diary.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    @Mock private SpeechToTextService speechToTextService;
    @Mock private UserRepository userRepository;
    @Mock private DiaryEntryRepository diaryEntryRepository;
    @Mock private DiaryMediaRepository diaryMediaRepository;
    @Mock private AiEmotionAnalysisRepository aiEmotionAnalysisRepository;
    @Mock private AiDiaryResultRepository aiDiaryResultRepository;
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
                .mode(DiaryEntryVO.Mode.DEFAULT)
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
        given(aiDraftGeneratorService.generate(anyString(), anyList(), any(), any())).willReturn(aiResult);

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
                .mode(DiaryEntryVO.Mode.IMAGE)
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
        given(aiDraftGeneratorService.generate(any(), anyList(), any(), any())).willReturn(aiResult);

        // when
        DiaryDraftResponse response = sut.createDraft(command);

        // then
        assertThat(response.mediaUrls()).containsExactly(signedUrl);
        verify(gcsStorageService).upload(mockImage, "uploads/images");
        verify(gcsStorageService).generateSignedUrl(blobPath);
    }

    @Test
    @DisplayName("텍스트, 이미지, 음성 모두 없으면 IllegalArgumentException 발생")
    void givenNoInput_whenCreateDraft_thenThrowIllegalArgumentException() {
        // given
        DiaryDraftCommand command = DiaryDraftCommand.builder()
                .userId(1L)
                .writtenAt(WRITTEN_AT)
                .mode(DiaryEntryVO.Mode.DEFAULT)
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
                .mode(DiaryEntryVO.Mode.DEFAULT)
                .build();

        // when & then
        assertThatThrownBy(() -> sut.createDraft(command))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─────────────────────────────────────────────────────
    // saveDiary 테스트
    // ─────────────────────────────────────────────────────

    @Test
    @DisplayName("DEFAULT 모드 일기 저장 시 diary_entries에 저장 후 DiaryResponse 반환")
    void givenDefaultModeCommand_whenSaveDiary_thenSaveAndReturnResponse() {
        // given
        Long userId = 1L;
        UserVO mockUser = UserVO.builder().id(userId).oauthProvider("google").oauthId("abc").build();

        DiarySaveCommand command = DiarySaveCommand.builder()
                .userId(userId)
                .rawContent("오늘 날씨가 맑았다.")
                .emoji("☀️")
                .writtenAt(WRITTEN_AT)
                .mode(DiaryEntryVO.Mode.DEFAULT)
                .build();

        DiaryEntryVO savedDiary = DiaryEntryVO.builder()
                .id(10L)
                .user(mockUser)
                .rawContent("오늘 날씨가 맑았다.")
                .emoji("☀️")
                .writtenAt(WRITTEN_AT)
                .mode(DiaryEntryVO.Mode.DEFAULT)
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
        assertThat(response.mode()).isEqualTo(DiaryEntryVO.Mode.DEFAULT);
        verify(diaryEntryRepository).save(any(DiaryEntryVO.class));
    }

    @Test
    @DisplayName("IMAGE 모드 저장 시 GCS 업로드 후 mediaUrls 포함하여 DiaryResponse 반환")
    void givenImageModeCommand_whenSaveDiary_thenUploadToGcsAndReturnResponseWithMediaUrls() throws IOException {
        // given
        Long userId = 1L;
        UserVO mockUser = UserVO.builder().id(userId).oauthProvider("google").oauthId("abc").build();
        MultipartFile mockImage = mock(MultipartFile.class);
        given(mockImage.getSize()).willReturn(1024L);

        DiarySaveCommand command = DiarySaveCommand.builder()
                .userId(userId)
                .writtenAt(WRITTEN_AT)
                .mode(DiaryEntryVO.Mode.IMAGE)
                .images(List.of(mockImage))
                .build();

        String blobPath = "uploads/images/photo.jpg";
        String signedUrl = "https://storage.googleapis.com/filly-media-bucket/" + blobPath + "?X-Goog-Signature=abc";
        DiaryEntryVO savedDiary = DiaryEntryVO.builder()
                .id(10L).user(mockUser).writtenAt(WRITTEN_AT)
                .mode(DiaryEntryVO.Mode.IMAGE).createdAt(LocalDateTime.now()).build();
        DiaryMediaVO savedMedia = DiaryMediaVO.builder()
                .id(1L).diary(savedDiary).type(DiaryMediaVO.Type.IMAGE)
                .gcsUrl(blobPath).fileSize(1024).build();

        given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));
        given(diaryEntryRepository.save(any(DiaryEntryVO.class))).willReturn(savedDiary);
        given(gcsStorageService.upload(mockImage, "uploads/images")).willReturn(blobPath);
        given(gcsStorageService.generateSignedUrl(blobPath)).willReturn(signedUrl);
        given(diaryMediaRepository.save(any(DiaryMediaVO.class))).willReturn(savedMedia);

        // when
        DiaryResponse response = sut.saveDiary(command);

        // then
        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.mediaUrls()).containsExactly(signedUrl);
        verify(gcsStorageService).upload(mockImage, "uploads/images");
        verify(gcsStorageService).generateSignedUrl(blobPath);
        verify(diaryMediaRepository).save(any(DiaryMediaVO.class));
    }

    @Test
    @DisplayName("존재하지 않는 userId로 저장 시 IllegalArgumentException 발생")
    void givenNonExistentUserId_whenSaveDiary_thenThrowIllegalArgumentException() {
        // given
        DiarySaveCommand command = DiarySaveCommand.builder()
                .userId(999L)
                .rawContent("테스트")
                .writtenAt(WRITTEN_AT)
                .mode(DiaryEntryVO.Mode.DEFAULT)
                .build();

        given(userRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> sut.saveDiary(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");

        verifyNoInteractions(diaryEntryRepository);
    }

    @Test
    @DisplayName("aiAnalysis 포함 시 ai_diary_analysis 저장")
    void givenAiAnalysis_whenSaveDiary_thenSaveEmotionAnalysis() throws JsonProcessingException {
        // given
        Long userId = 1L;
        UserVO mockUser = UserVO.builder().id(userId).oauthProvider("google").oauthId("abc").build();
        DiaryEntryVO savedDiary = DiaryEntryVO.builder()
                .id(10L).user(mockUser).rawContent("내용")
                .writtenAt(WRITTEN_AT).mode(DiaryEntryVO.Mode.DEFAULT)
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
                .userId(userId).rawContent("내용").writtenAt(WRITTEN_AT)
                .mode(DiaryEntryVO.Mode.DEFAULT).aiAnalysis(aiAnalysis).build();

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
    void givenGeneratedText_whenSaveDiary_thenSaveAiDiaryResult() {
        // given
        Long userId = 1L;
        UserVO mockUser = UserVO.builder().id(userId).oauthProvider("google").oauthId("abc").build();
        DiaryEntryVO savedDiary = DiaryEntryVO.builder()
                .id(10L).user(mockUser).rawContent("내용")
                .writtenAt(WRITTEN_AT).mode(DiaryEntryVO.Mode.DEFAULT)
                .createdAt(LocalDateTime.now()).build();

        DiarySaveCommand command = DiarySaveCommand.builder()
                .userId(userId).rawContent("내용").writtenAt(WRITTEN_AT)
                .mode(DiaryEntryVO.Mode.DEFAULT).generatedText("AI가 작성한 일기").build();

        given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));
        given(diaryEntryRepository.save(any(DiaryEntryVO.class))).willReturn(savedDiary);

        // when
        sut.saveDiary(command);

        // then
        verify(aiDiaryResultRepository).save(any(AiDiaryResultVO.class));
        verify(aiEmotionAnalysisRepository, never()).save(any());
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
                .mode(DiaryEntryVO.Mode.DEFAULT)
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
        assertThat(response.mode()).isEqualTo(DiaryEntryVO.Mode.DEFAULT);
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
                .mode(DiaryEntryVO.Mode.DEFAULT).createdAt(LocalDateTime.now()).build();
        DiaryEntryVO diary2 = DiaryEntryVO.builder()
                .id(2L).user(mockUser).rawContent("셋째 날").writtenAt(LocalDate.of(2026, 4, 3))
                .mode(DiaryEntryVO.Mode.DEFAULT).createdAt(LocalDateTime.now()).build();

        given(diaryEntryRepository.findByUser_IdAndWrittenAtBetweenOrderByWrittenAtAsc(
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
    @DisplayName("해당 월에 일기가 없으면 빈 목록 반환")
    void givenNoDiariesInMonth_whenGetDiariesByMonth_thenReturnEmptyList() {
        // given
        Long userId = 1L;

        given(diaryEntryRepository.findByUser_IdAndWrittenAtBetweenOrderByWrittenAtAsc(
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
                .writtenAt(WRITTEN_AT).mode(DiaryEntryVO.Mode.DEFAULT)
                .createdAt(LocalDateTime.now()).build();

        DiaryUpdateRequest request = new DiaryUpdateRequest("수정된 내용", "😊");
        given(diaryEntryRepository.findByIdAndUser_Id(diaryId, userId)).willReturn(Optional.of(diary));

        // when
        DiaryResponse response = sut.updateDiary(diaryId, userId, request);

        // then
        assertThat(response.rawContent()).isEqualTo("수정된 내용");
        assertThat(response.emoji()).isEqualTo("😊");
        assertThat(response.updatedAt()).isNotNull();
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
                .writtenAt(WRITTEN_AT).mode(DiaryEntryVO.Mode.DEFAULT)
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
    // deleteDiary 테스트
    // ─────────────────────────────────────────────────────

    @Test
    @DisplayName("본인 소유 일기 삭제 시 delete 호출")
    void givenValidDiary_whenDeleteDiary_thenDeleteCalled() {
        // given
        Long userId = 1L;
        Long diaryId = 10L;
        UserVO mockUser = UserVO.builder().id(userId).oauthProvider("google").oauthId("abc").build();
        DiaryEntryVO diary = DiaryEntryVO.builder()
                .id(diaryId).user(mockUser).rawContent("내용")
                .writtenAt(WRITTEN_AT).mode(DiaryEntryVO.Mode.DEFAULT)
                .createdAt(LocalDateTime.now()).build();

        given(diaryEntryRepository.findByIdAndUser_Id(diaryId, userId)).willReturn(Optional.of(diary));

        // when
        sut.deleteDiary(diaryId, userId);

        // then
        verify(diaryEntryRepository).delete(diary);
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
    }
}
