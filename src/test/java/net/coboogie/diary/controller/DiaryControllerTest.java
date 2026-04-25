package net.coboogie.diary.controller;

import net.coboogie.common.response.ApiResponse;
import net.coboogie.diary.dto.AiDraftResult;
import net.coboogie.diary.dto.DiaryDraftCommand;
import net.coboogie.diary.dto.DiaryDraftResponse;
import net.coboogie.diary.dto.DiaryResponse;
import net.coboogie.diary.dto.DiarySaveCommand;
import net.coboogie.diary.dto.DiaryUpdateRequest;
import net.coboogie.diary.service.DiaryService;
import net.coboogie.vo.DiaryEntryVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

/**
 * DiaryController 단위 테스트.
 * <p>
 * Spring 컨텍스트 없이 Mockito로 DiaryService를 모킹하여
 * 컨트롤러의 응답 래핑 및 위임 동작을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class DiaryControllerTest {

    @Mock
    private DiaryService diaryService;

    @InjectMocks
    private DiaryController diaryController;

    private static final Long USER_ID   = 1L;
    private static final Long DIARY_ID  = 10L;
    private static final LocalDate WRITTEN_AT = LocalDate.of(2026, 4, 11);
    private static final LocalDateTime NOW     = LocalDateTime.of(2026, 4, 11, 12, 0);

    // ─────────────────────────────────────────
    // createDraft
    // ─────────────────────────────────────────

    @Test
    @DisplayName("텍스트 입력으로 AI 초안 생성 요청 시 200과 초안 응답을 반환한다")
    void givenTextContent_whenCreateDraft_thenReturn200() {
        // given
        DiaryDraftResponse draftResponse = new DiaryDraftResponse(
                "AI가 생성한 일기 초안",
                new DiaryDraftResponse.AiAnalysis(
                        List.of(new AiDraftResult.EmotionScore("HAPPY", 0.9f)),
                        8,
                        List.of("한강", "치킨"),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        "행복한 하루",
                        "회고적"
                ),
                List.of()
        );
        given(diaryService.createDraft(any(DiaryDraftCommand.class))).willReturn(draftResponse);

        // when
        ResponseEntity<ApiResponse<DiaryDraftResponse>> response = diaryController.createDraft(
                USER_ID, "오늘 한강에서 치킨을 먹었다", null, null, "2026-04-11", "AI"
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ApiResponse<DiaryDraftResponse> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isTrue();
        assertThat(body.data().generatedText()).isEqualTo("AI가 생성한 일기 초안");
        assertThat(body.data().aiAnalysis().emotions().get(0).name()).isEqualTo("HAPPY");
        assertThat(body.data().aiAnalysis().happinessIndex()).isEqualTo(8);
    }

    // ─────────────────────────────────────────
    // getDiariesByMonth
    // ─────────────────────────────────────────

    @Test
    @DisplayName("월별 일기 목록 조회 시 해당 월의 일기 목록을 반환한다")
    void givenValidMonth_whenGetDiariesByMonth_thenReturnList() {
        // given
        DiaryResponse diary = makeDiaryResponse(DIARY_ID, "오늘 일기");
        given(diaryService.getDiariesByMonth(USER_ID, 2026, 4)).willReturn(List.of(diary));

        // when
        ResponseEntity<ApiResponse<List<DiaryResponse>>> response =
                diaryController.getDiariesByMonth(USER_ID, 2026, 4);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<DiaryResponse> data = response.getBody().data();
        assertThat(data).hasSize(1);
        assertThat(data.get(0).rawContent()).isEqualTo("오늘 일기");
    }

    @Test
    @DisplayName("해당 월에 일기가 없으면 빈 목록을 반환한다")
    void givenNoEntries_whenGetDiariesByMonth_thenReturnEmptyList() {
        // given
        given(diaryService.getDiariesByMonth(USER_ID, 2026, 4)).willReturn(List.of());

        // when
        ResponseEntity<ApiResponse<List<DiaryResponse>>> response =
                diaryController.getDiariesByMonth(USER_ID, 2026, 4);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).isEmpty();
    }

    // ─────────────────────────────────────────
    // getDiary
    // ─────────────────────────────────────────

    @Test
    @DisplayName("존재하는 일기 ID로 단건 조회 시 200과 일기를 반환한다")
    void givenExistingId_whenGetDiary_thenReturn200() {
        // given
        DiaryResponse diary = makeDiaryResponse(DIARY_ID, "단건 일기 내용");
        given(diaryService.getDiary(DIARY_ID, USER_ID)).willReturn(diary);

        // when
        ResponseEntity<ApiResponse<DiaryResponse>> response =
                diaryController.getDiary(USER_ID, DIARY_ID);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().id()).isEqualTo(DIARY_ID);
        assertThat(response.getBody().data().rawContent()).isEqualTo("단건 일기 내용");
    }

    @Test
    @DisplayName("존재하지 않는 일기 ID로 조회 시 NoSuchElementException이 발생한다")
    void givenNonExistingId_whenGetDiary_thenThrowException() {
        // given
        given(diaryService.getDiary(DIARY_ID, USER_ID))
                .willThrow(new NoSuchElementException("일기를 찾을 수 없습니다: " + DIARY_ID));

        // when & then
        assertThatThrownBy(() -> diaryController.getDiary(USER_ID, DIARY_ID))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("일기를 찾을 수 없습니다");
    }

    // ─────────────────────────────────────────
    // saveDiary
    // ─────────────────────────────────────────

    @Test
    @DisplayName("DEFAULT 모드로 일기 저장 시 200과 저장된 일기를 반환한다")
    void givenDefaultMode_whenSaveDiary_thenReturn200() {
        // given
        DiaryResponse saved = makeDiaryResponse(DIARY_ID, "저장된 일기 본문");
        given(diaryService.saveDiary(any(DiarySaveCommand.class))).willReturn(saved);

        // when
        ResponseEntity<ApiResponse<DiaryResponse>> response = diaryController.saveDiary(
                USER_ID, "저장된 일기 본문", null, "2026-04-11", "DEFAULT", null
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data().rawContent()).isEqualTo("저장된 일기 본문");
    }

    // ─────────────────────────────────────────
    // updateDiary
    // ─────────────────────────────────────────

    @Test
    @DisplayName("일기 수정 요청 시 200과 수정된 일기를 반환한다")
    void givenValidRequest_whenUpdateDiary_thenReturn200() {
        // given
        DiaryUpdateRequest request = new DiaryUpdateRequest("수정된 본문", "😊");
        DiaryResponse updated = makeDiaryResponse(DIARY_ID, "수정된 본문");
        given(diaryService.updateDiary(DIARY_ID, USER_ID, request)).willReturn(updated);

        // when
        ResponseEntity<ApiResponse<DiaryResponse>> response =
                diaryController.updateDiary(USER_ID, DIARY_ID, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().rawContent()).isEqualTo("수정된 본문");
    }

    @Test
    @DisplayName("존재하지 않는 일기 수정 시 NoSuchElementException이 발생한다")
    void givenNonExistingId_whenUpdateDiary_thenThrowException() {
        // given
        DiaryUpdateRequest request = new DiaryUpdateRequest("수정 내용", null);
        given(diaryService.updateDiary(DIARY_ID, USER_ID, request))
                .willThrow(new NoSuchElementException("일기를 찾을 수 없습니다: " + DIARY_ID));

        // when & then
        assertThatThrownBy(() -> diaryController.updateDiary(USER_ID, DIARY_ID, request))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("일기를 찾을 수 없습니다");
    }

    // ─────────────────────────────────────────
    // deleteDiary
    // ─────────────────────────────────────────

    @Test
    @DisplayName("일기 삭제 요청 시 200과 데이터 없는 성공 응답을 반환한다")
    void givenExistingId_whenDeleteDiary_thenReturn200() {
        // given - void 메서드는 기본적으로 doNothing이므로 별도 stub 불필요

        // when
        ResponseEntity<ApiResponse<Void>> response =
                diaryController.deleteDiary(USER_ID, DIARY_ID);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data()).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 일기 삭제 시 NoSuchElementException이 발생한다")
    void givenNonExistingId_whenDeleteDiary_thenThrowException() {
        // given
        willThrow(new NoSuchElementException("일기를 찾을 수 없습니다: " + DIARY_ID))
                .given(diaryService).deleteDiary(DIARY_ID, USER_ID);

        // when & then
        assertThatThrownBy(() -> diaryController.deleteDiary(USER_ID, DIARY_ID))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("일기를 찾을 수 없습니다");
    }

    // ─────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────

    /**
     * 테스트용 DiaryResponse 픽스처를 생성한다.
     */
    private DiaryResponse makeDiaryResponse(Long id, String rawContent) {
        return new DiaryResponse(
                id, rawContent, null, null,
                WRITTEN_AT, DiaryEntryVO.Mode.DEFAULT,
                NOW, null, List.of()
        );
    }
}
