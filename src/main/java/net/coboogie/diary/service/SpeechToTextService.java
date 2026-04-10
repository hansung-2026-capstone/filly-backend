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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.stream.Collectors;

/**
 * Google Cloud Speech-to-Text v2 Chirp 모델을 사용하는 음성 전사 서비스.
 * <p>
 * 인라인 오디오(60초 이하)만 처리한다. 오디오 인코딩 감지는 {@link AutoDetectDecodingConfig}에 위임한다.
 * 언어는 {@code ko-KR} 고정이며, recognizer는 기본값({@code _})을 사용한다.
 */
@Service
@RequiredArgsConstructor
public class SpeechToTextService {

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
        ByteString audioBytes = ByteString.copyFrom(voiceFile.getBytes());

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
}
