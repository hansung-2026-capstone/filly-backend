package net.coboogie.diary.service;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GcsStorageServiceTest {

    @Mock
    private Storage storage;

    @Mock
    private Blob blob;

    private GcsStorageService sut;

    @BeforeEach
    void setUp() {
        sut = new GcsStorageService(storage);
        ReflectionTestUtils.setField(sut, "bucketName", "filly-media-bucket");
        ReflectionTestUtils.setField(sut, "publicAvatarBucketName", "filly-public-media-bucket");
        ReflectionTestUtils.setField(sut, "storageEnabled", true);
    }

    @Test
    @DisplayName("같은 blob의 signed URL은 캐시되어 GCS 서명을 한 번만 수행한다")
    void givenSameBlobName_whenGenerateSignedUrlTwice_thenSignOnlyOnce() throws MalformedURLException {
        // given
        String blobName = "uploads/images/photo.png";
        URL signedUrl = new URL("https://storage.googleapis.com/filly-media-bucket/uploads/images/photo.png?signed");
        given(storage.signUrl(
                any(BlobInfo.class),
                anyLong(),
                any(TimeUnit.class),
                any(Storage.SignUrlOption.class)
        )).willReturn(signedUrl);

        // when
        String first = sut.generateSignedUrl(blobName);
        String second = sut.generateSignedUrl(blobName);

        // then
        assertThat(first).isEqualTo(signedUrl.toString());
        assertThat(second).isEqualTo(signedUrl.toString());
        verify(storage, times(1)).signUrl(
                any(BlobInfo.class),
                eq(1L),
                eq(TimeUnit.HOURS),
                any(Storage.SignUrlOption.class)
        );
    }

    @Test
    @DisplayName("공개 아바타 URL을 public bucket에서 읽어 data URL로 반환한다")
    void givenPublicAvatarUrl_whenGeneratePublicAvatarDataUrl_thenReadFromPublicBucket() {
        // given
        String avatarUrl = "https://storage.googleapis.com/filly-public-media-bucket/avatars/avatar.png";
        BlobId blobId = BlobId.of("filly-public-media-bucket", "avatars/avatar.png");
        given(storage.get(blobId)).willReturn(blob);
        given(blob.exists()).willReturn(true);
        given(blob.getContentType()).willReturn("image/png");
        given(blob.getContent()).willReturn("avatar".getBytes());

        // when
        String dataUrl = sut.generatePublicAvatarDataUrl(avatarUrl);

        // then
        assertThat(dataUrl).isEqualTo("data:image/png;base64,YXZhdGFy");
        verify(storage).get(blobId);
    }
}
