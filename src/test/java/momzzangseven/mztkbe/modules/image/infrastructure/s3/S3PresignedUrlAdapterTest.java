package momzzangseven.mztkbe.modules.image.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import momzzangseven.mztkbe.global.error.image.InvalidImageExtensionException;
import momzzangseven.mztkbe.global.error.image.InvalidImageRefTypeException;
import momzzangseven.mztkbe.modules.image.application.port.out.PresignedUrlWithKey;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
 * <p>리팩터링 후 어댑터는 (referenceType, uuid, extension)을 받아 내부에서 S3 경로를 조립하고 Content-Type을 결정한다.
 * S3Presigner를 Mock하여 실제 AWS 네트워크 호출 없이 다음을 검증한다:
 *
 * <ul>
 *   <li>PATH_PREFIX_MAP을 통해 각 referenceType이 올바른 S3 경로 prefix에 매핑되는지
 *   <li>MARKET(virtual type)처럼 prefix가 없는 타입 호출 시 예외 발생 여부
 *   <li>objectKey = prefix + uuid + "." + extension 조립이 올바른지
 *   <li>확장자별 Content-Type(MIME) 매핑이 올바른지
 *   <li>허용되지 않는 확장자 호출 시 예외 발생 여부
 *   <li>bucket, objectKey, contentType, expiryDuration이 AWS SDK에 올바르게 전달되는지
 *   <li>반환값 PresignedUrlWithKey에 presignedUrl과 tmpObjectKey가 올바르게 담기는지
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("S3PresignedUrlAdapter 단위 테스트")
class S3PresignedUrlAdapterTest {

  private static final String FAKE_URL =
      "https://test-bucket.s3.amazonaws.com/fake?X-Amz-Signature=abc";
  private static final String TEST_UUID = "test-uuid-1234";

  @Mock private S3Presigner s3Presigner;

  @Mock private PresignedPutObjectRequest presignedResult;

  @InjectMocks private S3PresignedUrlAdapter adapter;

  @BeforeEach
  void setUp() throws MalformedURLException {
    ReflectionTestUtils.setField(adapter, "bucket", "test-bucket");
    ReflectionTestUtils.setField(adapter, "expirySeconds", 300L);
    // lenient: 예외 발생 테스트(미허용 확장자, MARKET 가상 타입)는 S3 SDK 호출 전에 예외를 던지므로
    // 이 stub이 사용되지 않는다. STRICT_STUBS 정책의 UnnecessaryStubbingException을 방지한다.
    lenient().when(presignedResult.url()).thenReturn(new URL(FAKE_URL));
    lenient()
        .when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
        .thenReturn(presignedResult);
  }

  // =========================================================
  // S3 경로 prefix 매핑 검증
  // =========================================================

  @Nested
  @DisplayName("PATH_PREFIX_MAP — referenceType별 S3 경로 조립 검증")
  class PathPrefixTests {

    @Test
    @DisplayName("COMMUNITY_FREE → public/community/free/tmp/{uuid}.jpg")
    void generatePutPresignedUrl_communityFree_assemblesCorrectObjectKey() {
      ArgumentCaptor<PutObjectPresignRequest> captor =
          ArgumentCaptor.forClass(PutObjectPresignRequest.class);

      adapter.generatePutPresignedUrl(ImageReferenceType.COMMUNITY_FREE, TEST_UUID, "jpg");

      verify(s3Presigner).presignPutObject(captor.capture());
      assertThat(captor.getValue().putObjectRequest().key())
          .isEqualTo("public/community/free/tmp/" + TEST_UUID + ".jpg");
    }

    @Test
    @DisplayName("COMMUNITY_QUESTION → public/community/question/tmp/{uuid}.png")
    void generatePutPresignedUrl_communityQuestion_assemblesCorrectObjectKey() {
      ArgumentCaptor<PutObjectPresignRequest> captor =
          ArgumentCaptor.forClass(PutObjectPresignRequest.class);

      adapter.generatePutPresignedUrl(ImageReferenceType.COMMUNITY_QUESTION, TEST_UUID, "png");

      verify(s3Presigner).presignPutObject(captor.capture());
      assertThat(captor.getValue().putObjectRequest().key())
          .isEqualTo("public/community/question/tmp/" + TEST_UUID + ".png");
    }

    @Test
    @DisplayName("COMMUNITY_ANSWER → public/community/answer/tmp/{uuid}.jpeg")
    void generatePutPresignedUrl_communityAnswer_assemblesCorrectObjectKey() {
      ArgumentCaptor<PutObjectPresignRequest> captor =
          ArgumentCaptor.forClass(PutObjectPresignRequest.class);

      adapter.generatePutPresignedUrl(ImageReferenceType.COMMUNITY_ANSWER, TEST_UUID, "jpeg");

      verify(s3Presigner).presignPutObject(captor.capture());
      assertThat(captor.getValue().putObjectRequest().key())
          .isEqualTo("public/community/answer/tmp/" + TEST_UUID + ".jpeg");
    }

    @Test
    @DisplayName("MARKET_CLASS_THUMB → public/market/class/thumb/tmp/{uuid}.jpg")
    void generatePutPresignedUrl_marketClassThumb_assemblesCorrectObjectKey() {
      ArgumentCaptor<PutObjectPresignRequest> captor =
          ArgumentCaptor.forClass(PutObjectPresignRequest.class);

      adapter.generatePutPresignedUrl(ImageReferenceType.MARKET_CLASS_THUMB, TEST_UUID, "jpg");

      verify(s3Presigner).presignPutObject(captor.capture());
      assertThat(captor.getValue().putObjectRequest().key())
          .isEqualTo("public/market/class/thumb/tmp/" + TEST_UUID + ".jpg");
    }

    @Test
    @DisplayName("MARKET_CLASS_DETAIL → public/market/class/detail/tmp/{uuid}.png")
    void generatePutPresignedUrl_marketClassDetail_assemblesCorrectObjectKey() {
      ArgumentCaptor<PutObjectPresignRequest> captor =
          ArgumentCaptor.forClass(PutObjectPresignRequest.class);

      adapter.generatePutPresignedUrl(ImageReferenceType.MARKET_CLASS_DETAIL, TEST_UUID, "png");

      verify(s3Presigner).presignPutObject(captor.capture());
      assertThat(captor.getValue().putObjectRequest().key())
          .isEqualTo("public/market/class/detail/tmp/" + TEST_UUID + ".png");
    }

    @Test
    @DisplayName("MARKET_STORE_THUMB → public/market/store/thumb/tmp/{uuid}.jpg")
    void generatePutPresignedUrl_marketStoreThumb_assemblesCorrectObjectKey() {
      ArgumentCaptor<PutObjectPresignRequest> captor =
          ArgumentCaptor.forClass(PutObjectPresignRequest.class);

      adapter.generatePutPresignedUrl(ImageReferenceType.MARKET_STORE_THUMB, TEST_UUID, "jpg");

      verify(s3Presigner).presignPutObject(captor.capture());
      assertThat(captor.getValue().putObjectRequest().key())
          .isEqualTo("public/market/store/thumb/tmp/" + TEST_UUID + ".jpg");
    }

    @Test
    @DisplayName("MARKET_STORE_DETAIL → public/market/store/detail/tmp/{uuid}.png")
    void generatePutPresignedUrl_marketStoreDetail_assemblesCorrectObjectKey() {
      ArgumentCaptor<PutObjectPresignRequest> captor =
          ArgumentCaptor.forClass(PutObjectPresignRequest.class);

      adapter.generatePutPresignedUrl(ImageReferenceType.MARKET_STORE_DETAIL, TEST_UUID, "png");

      verify(s3Presigner).presignPutObject(captor.capture());
      assertThat(captor.getValue().putObjectRequest().key())
          .isEqualTo("public/market/store/detail/tmp/" + TEST_UUID + ".png");
    }

    @Test
    @DisplayName("USER_PROFILE → public/user/profile/tmp/{uuid}.jpg")
    void generatePutPresignedUrl_userProfile_assemblesCorrectObjectKey() {
      ArgumentCaptor<PutObjectPresignRequest> captor =
          ArgumentCaptor.forClass(PutObjectPresignRequest.class);

      adapter.generatePutPresignedUrl(ImageReferenceType.USER_PROFILE, TEST_UUID, "jpg");

      verify(s3Presigner).presignPutObject(captor.capture());
      assertThat(captor.getValue().putObjectRequest().key())
          .isEqualTo("public/user/profile/tmp/" + TEST_UUID + ".jpg");
    }

    @Test
    @DisplayName("WORKOUT → private/workout/{uuid}.jpg (tmp/ 서브폴더 없음)")
    void generatePutPresignedUrl_workout_usesPrivatePathWithoutTmpSubfolder() {
      ArgumentCaptor<PutObjectPresignRequest> captor =
          ArgumentCaptor.forClass(PutObjectPresignRequest.class);

      adapter.generatePutPresignedUrl(ImageReferenceType.WORKOUT, TEST_UUID, "jpg");

      verify(s3Presigner).presignPutObject(captor.capture());
      String key = captor.getValue().putObjectRequest().key();
      assertThat(key).isEqualTo("private/workout/" + TEST_UUID + ".jpg");
      assertThat(key).doesNotContain("tmp/");
    }

    @Test
    @DisplayName("MARKET_CLASS(virtual type)에는 prefix가 없으므로 InvalidImageRefTypeException 발생")
    void generatePutPresignedUrl_marketClassVirtualType_throwsInvalidImageRefTypeException() {
      assertThatThrownBy(
              () ->
                  adapter.generatePutPresignedUrl(
                      ImageReferenceType.MARKET_CLASS, TEST_UUID, "jpg"))
          .isInstanceOf(InvalidImageRefTypeException.class);
    }

    @Test
    @DisplayName("MARKET_STORE(virtual type)에는 prefix가 없으므로 InvalidImageRefTypeException 발생")
    void generatePutPresignedUrl_marketStoreVirtualType_throwsInvalidImageRefTypeException() {
      assertThatThrownBy(
              () ->
                  adapter.generatePutPresignedUrl(
                      ImageReferenceType.MARKET_STORE, TEST_UUID, "jpg"))
          .isInstanceOf(InvalidImageRefTypeException.class);
    }
  }

  // =========================================================
  // Content-Type 매핑 검증 (어댑터 내부 resolveContentType)
  // =========================================================

  @Nested
  @DisplayName("Content-Type 매핑 — extension → MIME 타입 변환 검증")
  class ContentTypeTests {

    @ParameterizedTest
    @CsvSource({
      "jpg, image/jpeg",
      "jpeg, image/jpeg",
      "png, image/png",
      "gif, image/gif",
      "heic, image/heic",
      "heif, image/heif"
    })
    @DisplayName("허용된 확장자별로 올바른 Content-Type이 SDK에 전달된다")
    void generatePutPresignedUrl_extensionMapsToCorrectContentType(
        String ext, String expectedContentType) {
      ArgumentCaptor<PutObjectPresignRequest> captor =
          ArgumentCaptor.forClass(PutObjectPresignRequest.class);

      adapter.generatePutPresignedUrl(ImageReferenceType.COMMUNITY_FREE, TEST_UUID, ext);

      verify(s3Presigner).presignPutObject(captor.capture());
      assertThat(captor.getValue().putObjectRequest().contentType()).isEqualTo(expectedContentType);
    }

    @ParameterizedTest
    @ValueSource(strings = {"webp", "bmp", "tiff", "svg"})
    @DisplayName("허용되지 않는 확장자 호출 시 InvalidImageExtensionException 발생")
    void generatePutPresignedUrl_unsupportedExtension_throwsInvalidImageExtensionException(
        String ext) {
      assertThatThrownBy(
              () ->
                  adapter.generatePutPresignedUrl(
                      ImageReferenceType.COMMUNITY_FREE, TEST_UUID, ext))
          .isInstanceOf(InvalidImageExtensionException.class);
    }
  }

  // =========================================================
  // AWS SDK 파라미터 전달 검증
  // =========================================================

  @Nested
  @DisplayName("AWS SDK 파라미터 전달 검증")
  class SdkParameterTests {

    @Test
    @DisplayName("bucket 이름이 S3 SDK PutObjectRequest에 올바르게 전달된다")
    void generatePutPresignedUrl_passesBucketToSdk() {
      ArgumentCaptor<PutObjectPresignRequest> captor =
          ArgumentCaptor.forClass(PutObjectPresignRequest.class);

      adapter.generatePutPresignedUrl(ImageReferenceType.COMMUNITY_FREE, TEST_UUID, "jpg");

      verify(s3Presigner).presignPutObject(captor.capture());
      assertThat(captor.getValue().putObjectRequest().bucket()).isEqualTo("test-bucket");
    }

    @Test
    @DisplayName("expirySeconds(300)이 Duration.ofSeconds(300)으로 변환되어 SDK에 전달된다")
    void generatePutPresignedUrl_passesExpiryDurationToSdk() {
      ArgumentCaptor<PutObjectPresignRequest> captor =
          ArgumentCaptor.forClass(PutObjectPresignRequest.class);

      adapter.generatePutPresignedUrl(ImageReferenceType.COMMUNITY_FREE, TEST_UUID, "jpg");

      verify(s3Presigner).presignPutObject(captor.capture());
      assertThat(captor.getValue().signatureDuration()).isEqualTo(Duration.ofSeconds(300));
    }

    @Test
    @DisplayName("expirySeconds 설정값 변경(600초)이 Duration에 정확히 반영된다")
    void generatePutPresignedUrl_reflectsChangedExpirySeconds() {
      ReflectionTestUtils.setField(adapter, "expirySeconds", 600L);
      ArgumentCaptor<PutObjectPresignRequest> captor =
          ArgumentCaptor.forClass(PutObjectPresignRequest.class);

      adapter.generatePutPresignedUrl(ImageReferenceType.WORKOUT, TEST_UUID, "jpg");

      verify(s3Presigner).presignPutObject(captor.capture());
      assertThat(captor.getValue().signatureDuration()).isEqualTo(Duration.ofSeconds(600));
    }
  }

  // =========================================================
  // 반환값 검증
  // =========================================================

  @Nested
  @DisplayName("반환값 PresignedUrlWithKey 검증")
  class ReturnValueTests {

    @Test
    @DisplayName("SDK가 반환한 presigned URL이 PresignedUrlWithKey.presignedUrl에 담긴다")
    void generatePutPresignedUrl_returnsPresignedUrlFromSdk() {
      PresignedUrlWithKey result =
          adapter.generatePutPresignedUrl(ImageReferenceType.COMMUNITY_FREE, TEST_UUID, "jpg");

      assertThat(result.presignedUrl()).isEqualTo(FAKE_URL);
    }

    @Test
    @DisplayName("조립된 objectKey가 PresignedUrlWithKey.tmpObjectKey에 담긴다")
    void generatePutPresignedUrl_returnsTmpObjectKeyMatchingAssembledPath() {
      PresignedUrlWithKey result =
          adapter.generatePutPresignedUrl(ImageReferenceType.COMMUNITY_FREE, TEST_UUID, "jpg");

      assertThat(result.tmpObjectKey())
          .isEqualTo("public/community/free/tmp/" + TEST_UUID + ".jpg");
    }

    @Test
    @DisplayName("MARKET_CLASS_THUMB: tmpObjectKey에 class/thumb prefix와 uuid가 포함된다")
    void generatePutPresignedUrl_marketClassThumb_returnsTmpObjectKeyWithClassThumbPrefixAndUuid() {
      PresignedUrlWithKey result =
          adapter.generatePutPresignedUrl(ImageReferenceType.MARKET_CLASS_THUMB, TEST_UUID, "png");

      assertThat(result.tmpObjectKey())
          .isEqualTo("public/market/class/thumb/tmp/" + TEST_UUID + ".png");
    }

    @Test
    @DisplayName("WORKOUT: tmpObjectKey에 private path와 uuid가 포함된다")
    void generatePutPresignedUrl_workout_returnsTmpObjectKeyWithPrivatePath() {
      PresignedUrlWithKey result =
          adapter.generatePutPresignedUrl(ImageReferenceType.WORKOUT, TEST_UUID, "heic");

      assertThat(result.tmpObjectKey()).isEqualTo("private/workout/" + TEST_UUID + ".heic");
    }
  }
}
