package net.coboogie.diary.service;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * Google Cloud Storage(GCS) 파일 업로드 서비스.
 * <p>
 * 버킷명은 {@code spring.cloud.gcp.storage.bucket} 프로퍼티에서 읽으며,
 * GCP 인증은 Cloud Run의 서비스 계정 또는 ADC(Application Default Credentials)로 처리된다.
 * {@code spring.cloud.gcp.storage.enabled=false}인 경우(로컬 개발) 실제 업로드를 건너뛰고 플레이스홀더 URL을 반환한다.
 */
@Service
@RequiredArgsConstructor
public class GcsStorageService {

    private final Storage storage;

    @Value("${spring.cloud.gcp.storage.bucket}")
    private String bucketName;

    @Value("${spring.cloud.gcp.storage.enabled:true}")
    private boolean storageEnabled;

    /**
     * 파일을 GCS에 업로드하고 공개 접근 가능한 URL을 반환한다.
     * 로컬 환경({@code storage.enabled=false})에서는 업로드를 생략하고 플레이스홀더 URL을 반환한다.
     *
     * @param file   업로드할 Multipart 파일
     * @param folder GCS 내 저장 경로 (예: {@code "diary/images"})
     * @return 업로드된 파일의 GCS 공개 URL
     * @throws IOException 파일 읽기 또는 GCS 업로드 실패 시
     */
    public String upload(MultipartFile file, String folder) throws IOException {
        String blobName = folder + "/" + UUID.randomUUID() + "_" + file.getOriginalFilename();

        if (!storageEnabled) {
            return "https://storage.googleapis.com/" + bucketName + "/" + blobName;
        }

        // 파일명 충돌 방지를 위해 UUID 접두사 적용
        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, blobName)
                .setContentType(file.getContentType())
                .build();
        storage.create(blobInfo, file.getBytes());
        return "https://storage.googleapis.com/" + bucketName + "/" + blobName;
    }
}
