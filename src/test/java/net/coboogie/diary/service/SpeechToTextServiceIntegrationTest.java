package net.coboogie.diary.service;

import com.google.cloud.speech.v2.SpeechClient;
import com.google.cloud.speech.v2.SpeechSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SpeechToTextService 실제 Google STT v2 API 호출 통합 테스트.
 *
 * 실행 전 필수:
 *   gcloud auth application-default login
 *
 * CI에서는 실행되지 않도록 @Disabled 처리. 로컬에서 수동 실행.
 */
//@Disabled("로컬 ADC 인증 필요 - 수동 실행 테스트")  // 실행 시 이 줄 삭제
class SpeechToTextServiceIntegrationTest {

    private static final String PROJECT_ID = "filly-492515";
    private static final String LOCATION   = "us-central1";

    private SpeechClient speechClient;
    private SpeechToTextService sut;

    @BeforeEach
    void setUp() throws IOException {
        SpeechSettings settings = SpeechSettings.newBuilder()
                .setEndpoint(LOCATION + "-speech.googleapis.com:443")
                .build();
        speechClient = SpeechClient.create(settings);
        sut = new SpeechToTextService(speechClient);
        ReflectionTestUtils.setField(sut, "projectId", PROJECT_ID);
        ReflectionTestUtils.setField(sut, "location", LOCATION);
    }

    @AfterEach
    void tearDown() throws Exception {
        speechClient.close();
    }

    @Test
    @DisplayName("무음 WAV 전송 시 API 연결이 성공하고 예외가 발생하지 않는다")
    void givenSilenceWav_whenTranscribe_thenNoException() throws IOException {
        // given - 1초 무음 WAV (16kHz, 16bit, mono)
        MockMultipartFile silenceWav = new MockMultipartFile(
                "voice", "silence.wav", "audio/wav", generateSilenceWav(16000, 1)
        );

        // when
        String result = sut.transcribe(silenceWav);

        // then - 무음이므로 빈 문자열. 예외 없이 통과하면 API 연결 성공
        System.out.println("전사 결과: '" + result + "'");
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("실제 음성 파일 경로를 지정하여 전사 결과를 출력한다")
    void givenRealAudioFile_whenTranscribe_thenPrintResult() throws IOException {
        // 테스트할 실제 음성 파일 경로로 교체하세요
        // 예: "C:/Users/777/test-audio.wav"
        java.io.File audioFile = new java.io.File("src/test/resources/test-audio.wav");

        if (!audioFile.exists()) {
            System.out.println("test-audio.wav 파일 없음 - 테스트 건너뜀");
            return;
        }

        MockMultipartFile voiceFile = new MockMultipartFile(
                "voice", audioFile.getName(), "audio/wav",
                java.nio.file.Files.readAllBytes(audioFile.toPath())
        );

        // when
        String result = sut.transcribe(voiceFile);

        // then
        System.out.println("전사 결과: '" + result + "'");
        assertThat(result).isNotBlank();
    }

    // ─────────────────────────────────────────────
    // WAV 생성 헬퍼
    // ─────────────────────────────────────────────

    /**
     * 무음 WAV 파일 바이트를 생성한다.
     *
     * @param sampleRate 샘플레이트 (Hz)
     * @param seconds    길이 (초)
     */
    private byte[] generateSilenceWav(int sampleRate, int seconds) throws IOException {
        int channels    = 1;
        int bitDepth    = 16;
        int dataSize    = sampleRate * seconds * channels * (bitDepth / 8);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);

        // RIFF 헤더
        dos.writeBytes("RIFF");
        writeIntLE(dos, 36 + dataSize);      // 전체 파일 크기 - 8
        dos.writeBytes("WAVE");

        // fmt 청크
        dos.writeBytes("fmt ");
        writeIntLE(dos, 16);                 // 청크 크기
        writeShortLE(dos, (short) 1);        // PCM
        writeShortLE(dos, (short) channels);
        writeIntLE(dos, sampleRate);
        writeIntLE(dos, sampleRate * channels * bitDepth / 8); // 바이트레이트
        writeShortLE(dos, (short) (channels * bitDepth / 8));  // 블록 얼라인
        writeShortLE(dos, (short) bitDepth);

        // data 청크
        dos.writeBytes("data");
        writeIntLE(dos, dataSize);
        dos.write(new byte[dataSize]); // 무음 (0으로 채움)

        dos.flush();
        return bos.toByteArray();
    }

    private void writeIntLE(DataOutputStream dos, int v) throws IOException {
        dos.write(v & 0xFF);
        dos.write((v >> 8) & 0xFF);
        dos.write((v >> 16) & 0xFF);
        dos.write((v >> 24) & 0xFF);
    }

    private void writeShortLE(DataOutputStream dos, short v) throws IOException {
        dos.write(v & 0xFF);
        dos.write((v >> 8) & 0xFF);
    }
}
