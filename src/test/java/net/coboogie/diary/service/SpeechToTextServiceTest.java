package net.coboogie.diary.service;

import com.google.cloud.speech.v2.RecognizeRequest;
import com.google.cloud.speech.v2.RecognizeResponse;
import com.google.cloud.speech.v2.SpeechClient;
import com.google.cloud.speech.v2.SpeechRecognitionAlternative;
import com.google.cloud.speech.v2.SpeechRecognitionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SpeechToTextServiceTest {

    @Mock private SpeechClient speechClient;
    @Mock private MultipartFile voiceFile;

    @InjectMocks
    private SpeechToTextService sut;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sut, "projectId", "test-project");
        ReflectionTestUtils.setField(sut, "location", "us-central1");
        // wav는 SUPPORTED_EXTENSIONS에 포함되어 getBytes() 경로로 진입하게 함
        given(voiceFile.getOriginalFilename()).willReturn("test.wav");
    }

    // ─────────────────────────────────────────────
    // 정상 전사
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("음성 파일이 있으면 Chirp 전사 결과를 반환한다")
    void givenVoiceFile_whenTranscribe_thenReturnTranscript() throws IOException {
        // given
        given(voiceFile.getBytes()).willReturn("dummy".getBytes());
        given(speechClient.recognize(any(RecognizeRequest.class)))
                .willReturn(responseWith("오늘 날씨가 정말 좋았어"));

        // when
        String result = sut.transcribe(voiceFile);

        // then
        assertThat(result).isEqualTo("오늘 날씨가 정말 좋았어");
    }

    @Test
    @DisplayName("복수 segment 결과는 공백으로 합산하여 반환한다")
    void givenMultipleSegments_whenTranscribe_thenJoinWithSpace() throws IOException {
        // given
        given(voiceFile.getBytes()).willReturn("dummy".getBytes());

        RecognizeResponse response = RecognizeResponse.newBuilder()
                .addResults(resultWith("첫 번째 문장."))
                .addResults(resultWith("두 번째 문장."))
                .build();
        given(speechClient.recognize(any(RecognizeRequest.class))).willReturn(response);

        // when
        String result = sut.transcribe(voiceFile);

        // then
        assertThat(result).isEqualTo("첫 번째 문장. 두 번째 문장.");
    }

    // ─────────────────────────────────────────────
    // 엣지 케이스
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("STT 결과가 없으면 빈 문자열을 반환한다")
    void givenEmptyResults_whenTranscribe_thenReturnEmptyString() throws IOException {
        // given
        given(voiceFile.getBytes()).willReturn("dummy".getBytes());
        given(speechClient.recognize(any(RecognizeRequest.class)))
                .willReturn(RecognizeResponse.newBuilder().build());

        // when
        String result = sut.transcribe(voiceFile);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("대안(alternatives)이 없는 결과는 무시하고 빈 문자열을 반환한다")
    void givenResultWithNoAlternatives_whenTranscribe_thenReturnEmptyString() throws IOException {
        // given
        given(voiceFile.getBytes()).willReturn("dummy".getBytes());

        RecognizeResponse response = RecognizeResponse.newBuilder()
                .addResults(SpeechRecognitionResult.newBuilder().build()) // alternatives 없음
                .build();
        given(speechClient.recognize(any(RecognizeRequest.class))).willReturn(response);

        // when
        String result = sut.transcribe(voiceFile);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getBytes() 실패 시 IOException을 그대로 전파한다")
    void givenGetBytesFails_whenTranscribe_thenPropagateIOException() throws IOException {
        // given
        given(voiceFile.getBytes()).willThrow(new IOException("디스크 읽기 실패"));

        // when & then
        assertThatThrownBy(() -> sut.transcribe(voiceFile))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("디스크 읽기 실패");
    }

    // ─────────────────────────────────────────────
    // RecognizeRequest 내용 검증
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("RecognizeRequest의 recognizer 이름이 STT v2 형식에 맞아야 한다")
    void whenTranscribe_thenRecognizerNameFollowsV2Format() throws IOException {
        // given
        given(voiceFile.getBytes()).willReturn("dummy".getBytes());
        given(speechClient.recognize(any(RecognizeRequest.class)))
                .willReturn(RecognizeResponse.newBuilder().build());

        ArgumentCaptor<RecognizeRequest> captor = ArgumentCaptor.forClass(RecognizeRequest.class);

        // when
        sut.transcribe(voiceFile);

        // then
        verify(speechClient).recognize(captor.capture());
        assertThat(captor.getValue().getRecognizer())
                .isEqualTo("projects/test-project/locations/us-central1/recognizers/_");
    }

    @Test
    @DisplayName("RecognizeRequest의 모델이 chirp이고 언어가 ko-KR이어야 한다")
    void whenTranscribe_thenRequestUsesChirpModelWithKorean() throws IOException {
        // given
        given(voiceFile.getBytes()).willReturn("dummy".getBytes());
        given(speechClient.recognize(any(RecognizeRequest.class)))
                .willReturn(RecognizeResponse.newBuilder().build());

        ArgumentCaptor<RecognizeRequest> captor = ArgumentCaptor.forClass(RecognizeRequest.class);

        // when
        sut.transcribe(voiceFile);

        // then
        verify(speechClient).recognize(captor.capture());
        RecognizeRequest request = captor.getValue();
        assertThat(request.getConfig().getModel()).isEqualTo("chirp");
        assertThat(request.getConfig().getLanguageCodesList()).containsExactly("ko-KR");
    }

    // ─────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────

    private static RecognizeResponse responseWith(String transcript) {
        return RecognizeResponse.newBuilder()
                .addResults(resultWith(transcript))
                .build();
    }

    private static SpeechRecognitionResult resultWith(String transcript) {
        return SpeechRecognitionResult.newBuilder()
                .addAlternatives(
                        SpeechRecognitionAlternative.newBuilder()
                                .setTranscript(transcript)
                                .build()
                )
                .build();
    }
}
