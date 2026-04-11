package net.coboogie.diary.service;

import com.google.cloud.speech.v2.AutoDetectDecodingConfig;
import com.google.cloud.speech.v2.RecognitionConfig;
import com.google.cloud.speech.v2.RecognizeRequest;
import com.google.cloud.speech.v2.RecognizeResponse;
import com.google.cloud.speech.v2.SpeechClient;
import com.google.cloud.speech.v2.SpeechRecognitionAlternative;
import com.google.cloud.speech.v2.SpeechRecognitionResult;
import com.google.protobuf.ByteString;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ws.schild.jave.Encoder;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Google Cloud Speech-to-Text v2 Chirp 모델을 사용하는 음성 전사 서비스.
 * <p>
 * 인라인 오디오(60초 이하)만 처리한다. M4A 등 지원되지 않는 포맷은 FLAC으로 변환 후 전송한다.
 * 언어는 {@code ko-KR} 고정이며, recognizer는 기본값({@code _})을 사용한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpeechToTextService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("flac", "wav", "mp3", "ogg", "webm", "amr");

    private final SpeechClient speechClient;

    @Value("${spring.cloud.gcp.project-id}")
    private String projectId;

    @Value("${gcp.speech.location:us-central1}")
    private String location;

    /**
     * 음성 파일을 Chirp 모델로 전사하여 텍스트를 반환한다.
     *
     * @param voiceFile 음성 파일 (MultipartFile)
     * @return 전사된 텍스트. 결과가 없으면 빈 문자열 반환
     * @throws IOException 파일 읽기 실패 시
     */
    public String transcribe(MultipartFile voiceFile) throws IOException {
        ByteString audioBytes = toSupportedAudioBytes(voiceFile);

        // recognizer 리소스명: projects/{project}/locations/{location}/recognizers/_
        // "_" 는 요청마다 config를 직접 지정하는 임시 recognizer
        String recognizerName = String.format(
                "projects/%s/locations/%s/recognizers/_", projectId, location
        );

        RecognitionConfig recognitionConfig = RecognitionConfig.newBuilder()
                .setModel("chirp")
                .addLanguageCodes("ko-KR")
                .setAutoDecodingConfig(AutoDetectDecodingConfig.getDefaultInstance())
                .build();

        RecognizeRequest request = RecognizeRequest.newBuilder()
                .setRecognizer(recognizerName)
                .setConfig(recognitionConfig)
                .setContent(audioBytes)
                .build();

        RecognizeResponse response = speechClient.recognize(request);

        return response.getResultsList().stream()
                .map(SpeechRecognitionResult::getAlternativesList)
                .filter(alts -> !alts.isEmpty())
                .map(alts -> alts.get(0))
                .map(SpeechRecognitionAlternative::getTranscript)
                .collect(Collectors.joining(" "))
                .trim();
    }

    /**
     * 지원되지 않는 포맷(M4A 등)은 FLAC으로 변환하여 반환한다.
     * 지원 포맷이면 그대로 바이트를 반환한다.
     */
    private ByteString toSupportedAudioBytes(MultipartFile voiceFile) throws IOException {
        String filename = voiceFile.getOriginalFilename() != null ? voiceFile.getOriginalFilename() : "";
        String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase() : "";

        if (SUPPORTED_EXTENSIONS.contains(ext)) {
            return ByteString.copyFrom(voiceFile.getBytes());
        }

        log.info("지원되지 않는 오디오 포맷({}), FLAC으로 변환합니다.", ext);
        File inputFile = File.createTempFile("stt-input-", "." + (ext.isEmpty() ? "tmp" : ext));
        File outputFile = File.createTempFile("stt-output-", ".flac");
        try {
            voiceFile.transferTo(inputFile);

            AudioAttributes audio = new AudioAttributes();
            audio.setCodec("flac");
            audio.setChannels(1);
            audio.setSamplingRate(16000);

            EncodingAttributes attrs = new EncodingAttributes();
            attrs.setOutputFormat("flac");
            attrs.setAudioAttributes(audio);

            new Encoder().encode(new MultimediaObject(inputFile), outputFile, attrs);
            return ByteString.copyFrom(Files.readAllBytes(outputFile.toPath()));
        } catch (Exception e) {
            throw new IOException("오디오 변환 실패: " + filename, e);
        } finally {
            inputFile.delete();
            outputFile.delete();
        }
    }
}
