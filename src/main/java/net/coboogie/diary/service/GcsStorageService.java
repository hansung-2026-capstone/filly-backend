package net.coboogie.diary.service;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Google Cloud Storage(GCS) 파일 업로드 및 서명 URL 발급 서비스.
 * <p>
 * 버킷은 비공개(public access 없음)로 운영되며, 클라이언트에는 V4 서명 URL을 제공한다.
 * 서명 URL은 1시간 유효하며, 서비스 계정 또는 ADC(Application Default Credentials)로 서명된다.
 * {@code spring.cloud.gcp.storage.enabled=false}인 경우(로컬 개발) 실제 GCS 호출을 건너뛰고 플레이스홀더를 반환한다.
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
     * 파일을 GCS에 업로드하고 버킷 내 blob 경로(객체명)를 반환한다.
     * 반환된 경로는 DB에 저장되며, 클라이언트에 노출할 때는 {@link #generateSignedUrl(String)}로 변환한다.
     * 로컬 환경({@code storage.enabled=false})에서는 업로드를 생략하고 경로만 생성하여 반환한다.
     *
     * @param file   업로드할 Multipart 파일
     * @param folder GCS 내 저장 폴더 (예: {@code "uploads/images"})
     * @return GCS blob 경로 (예: {@code "uploads/images/uuid_filename.png"})
     * @throws IOException 파일 읽기 또는 GCS 업로드 실패 시
     */
    public String upload(MultipartFile file, String folder) throws IOException {
        String blobName = folder + "/" + UUID.randomUUID() + "_" + file.getOriginalFilename();

        if (!storageEnabled) {
            return blobName;
        }

        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, blobName)
                .setContentType(file.getContentType())
                .build();
        storage.create(blobInfo, file.getBytes());
        return blobName;
    }

    /**
     * GCS blob 경로에 대한 V4 서명 URL을 생성하여 반환한다.
     * 서명 URL은 1시간 후 만료된다.
     * 로컬 환경({@code storage.enabled=false})에서는 플레이스홀더 URL을 반환한다.
     *
     * @param blobName GCS blob 경로 (예: {@code "uploads/images/uuid_filename.png"})
     * @return 1시간 유효한 V4 서명 URL 문자열
     */
    public String generateSignedUrl(String blobName) {
        if (!storageEnabled) {
            return "https://storage.googleapis.com/" + bucketName + "/" + blobName;
        }
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, blobName)).build();
        URL signedUrl = storage.signUrl(
                blobInfo,
                1, TimeUnit.HOURS,
                Storage.SignUrlOption.withV4Signature()
        );
        return signedUrl.toString();
    }
}
