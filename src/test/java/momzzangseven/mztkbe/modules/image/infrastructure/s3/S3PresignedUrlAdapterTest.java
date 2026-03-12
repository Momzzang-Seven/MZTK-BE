package momzzangseven.mztkbe.modules.image.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * S3PresignedUrlAdapter 단위 테스트.
 *
 * <p>S3Presigner를 Mock하여 실제 AWS 네트워크 호출 없이 다음을 검증한다:
 *
 * <ul>
 *   <li>bucket, objectKey, contentType이 AWS SDK에 올바르게 전달되는지
 *   <li>expirySeconds가 Duration으로 올바르게 변환되는지
 *   <li>SDK가 반환한 presigned URL이 그대로 반환되는지
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("S3PresignedUrlAdapter 단위 테스트")
class S3PresignedUrlAdapterTest {

  private static final String FAKE_URL =
      "https://test-bucket.s3.amazonaws.com/fake?X-Amz-Signature=abc";

  @Mock private S3Presigner s3Presigner;

  @Mock private PresignedPutObjectRequest presignedResult;

  @InjectMocks private S3PresignedUrlAdapter adapter;

  @BeforeEach
  void setUp() throws MalformedURLException {
    ReflectionTestUtils.setField(adapter, "bucket", "test-bucket");
    ReflectionTestUtils.setField(adapter, "expirySeconds", 300L);
    // PresignedPutObjectRequest.url()은 java.net.URL을 반환하므로 사전에 스터빙
    given(presignedResult.url()).willReturn(new URL(FAKE_URL));
    given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
        .willReturn(presignedResult);
  }

  @Nested
  @DisplayName("AWS SDK 파라미터 전달 검증")
  class SdkParameterTests {

    @Test
    @DisplayName("bucket 이름이 S3 SDK PutObjectRequest에 올바르게 전달된다")
    void generatePutPresignedUrl_passesBucketToSdk() {
      ArgumentCaptor<PutObjectPresignRequest> captor =
          ArgumentCaptor.forClass(PutObjectPresignRequest.class);

      adapter.generatePutPresignedUrl("public/community/free/tmp/uuid.jpg", "image/jpeg");

      verify(s3Presigner).presignPutObject(captor.capture());
      assertThat(captor.getValue().putObjectRequest().bucket()).isEqualTo("test-bucket");
    }

    @Test
    @DisplayName("objectKey가 S3 SDK PutObjectRequest에 올바르게 전달된다")
    void generatePutPresignedUrl_passesObjectKeyToSdk() {
      ArgumentCaptor<PutObjectPresignRequest> captor =
          ArgumentCaptor.forClass(PutObjectPresignRequest.class);

      adapter.generatePutPresignedUrl("public/community/free/tmp/uuid.jpg", "image/jpeg");

      verify(s3Presigner).presignPutObject(captor.capture());
      assertThat(captor.getValue().putObjectRequest().key())
          .isEqualTo("public/community/free/tmp/uuid.jpg");
    }

    @Test
    @DisplayName("contentType이 S3 SDK PutObjectRequest에 올바르게 전달된다")
    void generatePutPresignedUrl_passesContentTypeToSdk() {
      ArgumentCaptor<PutObjectPresignRequest> captor =
          ArgumentCaptor.forClass(PutObjectPresignRequest.class);

      adapter.generatePutPresignedUrl("public/community/free/tmp/uuid.jpg", "image/png");

      verify(s3Presigner).presignPutObject(captor.capture());
      assertThat(captor.getValue().putObjectRequest().contentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("expirySeconds(300)이 Duration.ofSeconds(300)으로 변환되어 SDK에 전달된다")
    void generatePutPresignedUrl_passesExpiryDurationToSdk() {
      ArgumentCaptor<PutObjectPresignRequest> captor =
          ArgumentCaptor.forClass(PutObjectPresignRequest.class);

      adapter.generatePutPresignedUrl("public/community/free/tmp/uuid.jpg", "image/jpeg");

      verify(s3Presigner).presignPutObject(captor.capture());
      assertThat(captor.getValue().signatureDuration()).isEqualTo(Duration.ofSeconds(300));
    }

    @Test
    @DisplayName("expirySeconds 설정값 변경(600초)이 Duration에 정확히 반영된다")
    void generatePutPresignedUrl_reflectsChangedExpirySeconds() {
      ReflectionTestUtils.setField(adapter, "expirySeconds", 600L);
      ArgumentCaptor<PutObjectPresignRequest> captor =
          ArgumentCaptor.forClass(PutObjectPresignRequest.class);

      adapter.generatePutPresignedUrl("private/workout/uuid.jpg", "image/jpeg");

      verify(s3Presigner).presignPutObject(captor.capture());
      assertThat(captor.getValue().signatureDuration()).isEqualTo(Duration.ofSeconds(600));
    }
  }

  @Nested
  @DisplayName("반환값 검증")
  class ReturnValueTests {

    @Test
    @DisplayName("SDK가 반환한 presigned URL 문자열이 그대로 반환된다")
    void generatePutPresignedUrl_returnsUrlFromSdk() {
      String result =
          adapter.generatePutPresignedUrl("public/community/free/tmp/uuid.jpg", "image/jpeg");

      assertThat(result).isEqualTo(FAKE_URL);
    }
  }
}
