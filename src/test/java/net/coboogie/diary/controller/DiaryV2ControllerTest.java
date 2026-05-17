package net.coboogie.diary.controller;

import net.coboogie.common.response.ApiResponse;
import net.coboogie.diary.dto.AiDraftResult;
import net.coboogie.diary.dto.DiaryDraftCommand;
import net.coboogie.diary.dto.DiaryDraftResponse;
import net.coboogie.diary.service.DiaryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DiaryV2ControllerTest {

    @Mock
    private DiaryService diaryService;

    @InjectMocks
    private DiaryV2Controller diaryV2Controller;

    @Test
    @DisplayName("v2 초안 생성 요청 시 200과 초안 응답을 반환한다")
    void givenTextContent_whenCreateDraft_thenReturn200() {
        // given
        DiaryDraftResponse draftResponse = new DiaryDraftResponse(
                "AI가 생성한 v2 일기 초안",
                new DiaryDraftResponse.AiAnalysis(
                        List.of(new AiDraftResult.EmotionScore("HAPPY", 0.9f)),
                        8,
                        List.of("산책"),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        "행복한 하루",
                        "실시간"
                ),
                List.of()
        );
        given(diaryService.createDraftV2(any(DiaryDraftCommand.class))).willReturn(draftResponse);

        // when
        ResponseEntity<ApiResponse<DiaryDraftResponse>> response = diaryV2Controller.createDraft(
                1L, "오늘 산책을 했다", null, null, "2026-04-11"
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data().generatedText()).isEqualTo("AI가 생성한 v2 일기 초안");

        ArgumentCaptor<DiaryDraftCommand> captor = ArgumentCaptor.forClass(DiaryDraftCommand.class);
        verify(diaryService).createDraftV2(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(1L);
        assertThat(captor.getValue().textContent()).isEqualTo("오늘 산책을 했다");
        assertThat(captor.getValue().writtenAt()).isEqualTo(LocalDate.of(2026, 4, 11));
    }
}
