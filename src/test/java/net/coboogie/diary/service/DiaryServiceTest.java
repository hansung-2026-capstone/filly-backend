package net.coboogie.diary.service;

import net.coboogie.diary.dto.AiDraftResult;
import net.coboogie.diary.dto.DiaryDraftCommand;
import net.coboogie.diary.dto.DiaryDraftResponse;
import net.coboogie.fillybackend.vo.DiaryEntryVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiaryServiceTest {

    @Mock private GcsStorageService gcsStorageService;
    @Mock private AiDraftGeneratorService aiDraftGeneratorService;

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
                "HAPPY", 0.85f, 8,
                List.of("날씨", "햇살", "기분")
        );
        given(aiDraftGeneratorService.generate(anyString(), anyList(), any())).willReturn(aiResult);

        // when
        DiaryDraftResponse response = sut.createDraft(command);

        // then
        assertThat(response.generatedText()).isEqualTo("오늘 따뜻한 햇살이 기분을 밝게 해주었다.");
        assertThat(response.aiAnalysis().emotionType()).isEqualTo("HAPPY");
        assertThat(response.aiAnalysis().moodIndex()).isEqualTo(8);
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

        String gcsUrl = "https://storage.googleapis.com/filly-media-bucket/diary/images/uuid_photo.jpg";
        AiDraftResult aiResult = new AiDraftResult("이미지 속 풍경이 아름다웠다.", "CALM", 0.7f, 6, List.of("풍경"));

        given(gcsStorageService.upload(mockImage, "diary/images")).willReturn(gcsUrl);
        given(aiDraftGeneratorService.generate(any(), anyList(), any())).willReturn(aiResult);

        // when
        DiaryDraftResponse response = sut.createDraft(command);

        // then
        assertThat(response.mediaUrls()).containsExactly(gcsUrl);
        verify(gcsStorageService).upload(mockImage, "diary/images");
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
}
