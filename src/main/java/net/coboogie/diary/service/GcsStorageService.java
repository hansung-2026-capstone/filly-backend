package net.coboogie.diary.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URL;
import java.util.Base64;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Google Cloud Storage(GCS) 파일 업로드 및 서명 URL 발급 서비스.
 * <p>
 * 버킷은 비공개(public access 없음)로 운영되며, 클라이언트에는 V4 서명 URL을 제공한다.
 * 서명 URL은 1시간 유효하며, 서비스 계정 또는 ADC(Application Default Credentials)로 서명된다.
 * {@code spring.cloud.gcp.storage.enabled=false}인 경우(로컬 개발) 실제 GCS 호출을 건너뛰고 플레이스홀더를 반환한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GcsStorageService {

    private static final int SIGNED_URL_EXPIRATION_HOURS = 1;
    private static final int SIGNED_URL_CACHE_MINUTES = 50;
    private static final long SIGNED_URL_CACHE_MAX_SIZE = 10_000;

    private final Storage storage;
    private final Cache<String, String> signedUrlCache = Caffeine.newBuilder()
            .expireAfterWrite(SIGNED_URL_CACHE_MINUTES, TimeUnit.MINUTES)
            .maximumSize(SIGNED_URL_CACHE_MAX_SIZE)
            .build();

    @Value("${spring.cloud.gcp.storage.bucket}")
    private String bucketName;

    @Value("${app.gcs.public-avatar-bucket}")
    private String publicAvatarBucketName;

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
            log.info("GCS upload skipped storageEnabled=false blobName={}", blobName);
            return blobName;
        }

        log.info("GCS upload start bucket={} blobName={} contentType={} size={}",
                bucketName, blobName, file.getContentType(), file.getSize());
        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, blobName)
                .setContentType(file.getContentType())
                .build();
        storage.create(blobInfo, file.getBytes());
        log.info("GCS upload complete bucket={} blobName={}", bucketName, blobName);
        return blobName;
    }

    /**
     * GCS blob 경로에 대한 V4 서명 URL을 생성하여 반환한다.
     * 서명 URL은 1시간 후 만료된다.
     * 로컬 환경({@code storage.enabled=false})에서는 플레이스홀더 URL을 반환한다.
     * <p>
     * DB에 full URL({@code https://storage.googleapis.com/bucket/...})이 저장된 레거시 데이터도 처리한다.
     * full URL이 입력되면 blob 경로만 추출하여 서명한다.
     *
     * @param blobName GCS blob 경로 (예: {@code "uploads/images/uuid_filename.png"})
     *                 또는 레거시 full URL (예: {@code "https://storage.googleapis.com/bucket/..."})
     * @return 1시간 유효한 V4 서명 URL 문자열
     */
    public String generateSignedUrl(String blobName) {
        if (!storageEnabled) {
            log.info("GCS signed URL skipped storageEnabled=false blobName={}", blobName);
            return "https://storage.googleapis.com/" + bucketName + "/" + blobName;
        }
        String resolvedBlobName = stripBucketPrefix(blobName);
        return signedUrlCache.get(resolvedBlobName, this::createSignedUrl);
    }

    private String createSignedUrl(String resolvedBlobName) {
        log.info("GCS signed URL start bucket={} blobName={}", bucketName, resolvedBlobName);
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, resolvedBlobName)).build();
        URL signedUrl = storage.signUrl(
                blobInfo,
                SIGNED_URL_EXPIRATION_HOURS, TimeUnit.HOURS,
                Storage.SignUrlOption.withV4Signature()
        );
        log.info("GCS signed URL complete bucket={} blobName={}", bucketName, resolvedBlobName);
        return signedUrl.toString();
    }

    /**
     * GCS blob 경로를 공개 URL로 변환한다.
     * 이미 HTTP URL이면 그대로 반환하며, GCS 서명이나 네트워크 호출은 수행하지 않는다.
     *
     * @param blobName GCS blob 경로 또는 기존 URL
     * @return 공개 접근용 GCS URL
     */
    public String generatePublicUrl(String blobName) {
        if (blobName == null || blobName.isBlank() || isHttpUrl(blobName)) {
            return blobName;
        }
        String resolvedBlobName = stripBucketPrefix(blobName);
        return "https://storage.googleapis.com/" + bucketName + "/" + resolvedBlobName;
    }

    /**
     * 아바타 이미지를 공개 버킷에 업로드하고 공개 URL을 반환한다.
     * 사용자 업로드 미디어는 비공개 버킷을 계속 사용하고, 아바타만 별도 public bucket에 저장한다.
     *
     * @param bytes       업로드할 이미지 바이트
     * @param filename    저장 파일명
     * @param contentType 파일 MIME 타입
     * @return 공개 접근 가능한 아바타 URL
     */
    public String uploadPublicAvatarBytes(byte[] bytes, String filename, String contentType) {
        String blobName = "avatars/" + UUID.randomUUID() + "_" + filename;

        if (!storageEnabled) {
            log.info("GCS public avatar upload skipped storageEnabled=false blobName={}", blobName);
            return generatePublicAvatarUrl(blobName);
        }

        log.info("GCS public avatar upload start bucket={} blobName={} contentType={} size={}",
                publicAvatarBucketName, blobName, contentType, bytes.length);
        BlobInfo blobInfo = BlobInfo.newBuilder(publicAvatarBucketName, blobName)
                .setContentType(contentType)
                .build();
        storage.create(blobInfo, bytes);
        log.info("GCS public avatar upload complete bucket={} blobName={}", publicAvatarBucketName, blobName);
        return generatePublicAvatarUrl(blobName);
    }

    /**
     * 공개 아바타 버킷의 blob 경로를 공개 URL로 변환한다.
     *
     * @param blobName 공개 아바타 버킷 내 blob 경로 또는 기존 URL
     * @return 공개 접근 가능한 아바타 URL
     */
    public String generatePublicAvatarUrl(String blobName) {
        if (blobName == null || blobName.isBlank() || isHttpUrl(blobName)) {
            return blobName;
        }
        String resolvedBlobName = stripBucketPrefix(blobName, publicAvatarBucketName);
        return "https://storage.googleapis.com/" + publicAvatarBucketName + "/" + resolvedBlobName;
    }

    /**
     * GCS 객체를 삭제한다.
     * 로컬 환경({@code storage.enabled=false})에서는 실제 삭제를 생략한다.
     *
     * @param blobName GCS blob 경로 또는 레거시 full URL
     * @return 삭제되었거나 로컬 환경에서 삭제가 생략되면 true, 객체가 없거나 실패하면 false
     */
    public boolean delete(String blobName) {
        String resolvedBlobName = stripBucketPrefix(blobName);
        signedUrlCache.invalidate(resolvedBlobName);
        if (!storageEnabled) {
            log.info("GCS delete skipped storageEnabled=false blobName={}", resolvedBlobName);
            return true;
        }
        try {
            boolean deleted = storage.delete(BlobId.of(bucketName, resolvedBlobName));
            log.info("GCS delete complete bucket={} blobName={} deleted={}", bucketName, resolvedBlobName, deleted);
            return deleted;
        } catch (RuntimeException e) {
            log.warn("GCS delete failed bucket={} blobName={}", bucketName, resolvedBlobName, e);
            return false;
        }
    }

    /**
     * GCS 객체를 data URL로 변환한다.
     * <p>
     * 공유 이미지 생성처럼 브라우저 캔버스에 이미지를 그리는 흐름에서는 외부 GCS URL의 CORS
     * 헤더에 의존하면 캔버스가 오염될 수 있으므로, 응답 본문에 직접 포함 가능한 data URL을 사용한다.
     *
     * @param blobName GCS blob 경로 또는 레거시 full URL
     * @return {@code data:<content-type>;base64,...} 형식의 이미지 URL
     */
    public String generateDataUrl(String blobName) {
        if (!storageEnabled) {
            log.info("GCS data URL skipped storageEnabled=false blobName={}", blobName);
            return generateSignedUrl(blobName);
        }
        String resolvedBlobName = stripBucketPrefix(blobName);
        log.info("GCS data URL start bucket={} blobName={}", bucketName, resolvedBlobName);
        Blob blob = storage.get(BlobId.of(bucketName, resolvedBlobName));
        if (blob == null || !blob.exists()) {
            throw new NoSuchElementException("GCS 객체를 찾을 수 없습니다: " + resolvedBlobName);
        }
        String contentType = blob.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }
        String encoded = Base64.getEncoder().encodeToString(blob.getContent());
        log.info("GCS data URL complete bucket={} blobName={}", bucketName, resolvedBlobName);
        return "data:" + contentType + ";base64," + encoded;
    }

    /**
     * 바이트 배열을 GCS에 업로드하고 버킷 내 blob 경로를 반환한다.
     * 로컬 환경({@code storage.enabled=false})에서는 업로드를 생략하고 경로만 반환한다.
     *
     * @param bytes       업로드할 바이트 배열
     * @param folder      GCS 내 저장 폴더 (예: {@code "avatars"})
     * @param filename    저장 파일명 (예: {@code "avatar.png"})
     * @param contentType 파일 MIME 타입 (예: {@code "image/png"})
     * @return GCS blob 경로 (예: {@code "avatars/uuid_avatar.png"})
     */
    public String uploadBytes(byte[] bytes, String folder, String filename, String contentType) {
        String blobName = folder + "/" + UUID.randomUUID() + "_" + filename;

        if (!storageEnabled) {
            log.info("GCS byte upload skipped storageEnabled=false blobName={}", blobName);
            return blobName;
        }

        log.info("GCS byte upload start bucket={} blobName={} contentType={} size={}",
                bucketName, blobName, contentType, bytes.length);
        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, blobName)
                .setContentType(contentType)
                .build();
        storage.create(blobInfo, bytes);
        log.info("GCS byte upload complete bucket={} blobName={}", bucketName, blobName);
        return blobName;
    }

    /**
     * full URL에서 bucket prefix를 제거하고 blob 경로만 반환한다.
     * 이미 blob 경로이면 그대로 반환한다.
     *
     * @param blobName blob 경로 또는 full GCS URL
     * @return blob 경로
     */
    private String stripBucketPrefix(String blobName) {
        return stripBucketPrefix(blobName, bucketName);
    }

    private String stripBucketPrefix(String blobName, String bucket) {
        String prefix = "https://storage.googleapis.com/" + bucket + "/";
        if (blobName.startsWith(prefix)) {
            return blobName.substring(prefix.length());
        }
        return blobName;
    }

    private boolean isHttpUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }
}
