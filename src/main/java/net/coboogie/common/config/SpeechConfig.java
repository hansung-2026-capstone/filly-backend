package net.coboogie.common.config;

import com.google.cloud.speech.v2.SpeechClient;
import com.google.cloud.speech.v2.SpeechSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Google Cloud Speech-to-Text v2 클라이언트 설정.
 * <p>
 * Chirp 모델은 리전 엔드포인트({location}-speech.googleapis.com:443)에서만 지원된다.
 * 기본 {@code SpeechClient.create()}는 글로벌 엔드포인트를 사용하므로
 * {@code SpeechSettings}으로 리전 엔드포인트를 명시적으로 지정해야 한다.
 * 인증은 GCP 환경의 Application Default Credentials(ADC)를 그대로 사용한다.
 */
@Configuration
public class SpeechConfig {

    @Value("${gcp.speech.location:us-central1}")
    private String location;

    @Bean(destroyMethod = "close")
    public SpeechClient speechClient() throws IOException {
        SpeechSettings settings = SpeechSettings.newBuilder()
                .setEndpoint(location + "-speech.googleapis.com:443")
                .build();
        return SpeechClient.create(settings);
    }
}
