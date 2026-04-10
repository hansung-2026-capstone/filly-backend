package net.coboogie.common.config;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 로컬 개발 환경 전용 설정.
 * <p>
 * {@code local} 프로파일에서만 활성화된다.
 * GCS는 로컬에서 비활성화({@code spring.cloud.gcp.storage.enabled=false})되어
 * {@link Storage} 빈이 자동 등록되지 않으므로, ADC(Application Default Credentials)를
 * 사용하는 Storage 인스턴스를 수동으로 제공한다.
 * 실제 GCS 업로드를 시도하면 ADC 설정 여부에 따라 동작하거나 예외가 발생한다.
 */
@Configuration
@Profile("local")
public class LocalConfig {

    /**
     * 로컬 개발 환경용 GCS Storage Bean.
     * ADC(gcloud auth application-default login)로 인증하며,
     * 애플리케이션 컨텍스트 로드 시점에는 실패하지 않는다.
     *
     * @return GCS Storage 인스턴스
     */
    @Bean
    public Storage storage() {
        return StorageOptions.getDefaultInstance().getService();
    }
}
