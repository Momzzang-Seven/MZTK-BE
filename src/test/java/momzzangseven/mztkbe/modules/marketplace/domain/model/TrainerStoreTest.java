package momzzangseven.mztkbe.modules.marketplace.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("TrainerStore 도메인 모델 단위 테스트")
class TrainerStoreTest {

  // ============================================
  // Test Fixtures
  // ============================================

  private static final Long VALID_TRAINER_ID = 1L;
  private static final String VALID_STORE_NAME = "PT Studio";
  private static final String VALID_ADDRESS = "서울시 강남구 역삼동 123";
  private static final String VALID_DETAIL_ADDRESS = "2층 201호";
  private static final Double VALID_LATITUDE = 37.4979;
  private static final Double VALID_LONGITUDE = 127.0276;
  private static final String VALID_PHONE = "010-1234-5678";
  private static final String VALID_HOMEPAGE = "https://example.com";
  private static final String VALID_INSTAGRAM = "https://instagram.com/trainer";
  private static final String VALID_X_URL = "https://x.com/trainer";

  private TrainerStore createValidStore() {
    return TrainerStore.create(
        VALID_TRAINER_ID,
        VALID_STORE_NAME,
        VALID_ADDRESS,
        VALID_DETAIL_ADDRESS,
        VALID_LATITUDE,
        VALID_LONGITUDE,
        VALID_PHONE,
        VALID_HOMEPAGE,
        VALID_INSTAGRAM,
        VALID_X_URL);
  }

  // ============================================
  // create() — 성공 케이스
  // ============================================

  @Nested
  @DisplayName("create() - 성공 케이스")
  class CreateSuccessCases {

    @Test
    @DisplayName("유효한 필드로 TrainerStore를 생성한다")
    void create_withValidFields_succeeds() {
      // when
      TrainerStore store = createValidStore();

      // then
      assertThat(store.getTrainerId()).isEqualTo(VALID_TRAINER_ID);
      assertThat(store.getStoreName()).isEqualTo(VALID_STORE_NAME);
      assertThat(store.getAddress()).isEqualTo(VALID_ADDRESS);
      assertThat(store.getDetailAddress()).isEqualTo(VALID_DETAIL_ADDRESS);
      assertThat(store.getLatitude()).isEqualTo(VALID_LATITUDE);
      assertThat(store.getLongitude()).isEqualTo(VALID_LONGITUDE);
      assertThat(store.getPhoneNumber()).isEqualTo(VALID_PHONE);
      assertThat(store.getHomepageUrl()).isEqualTo(VALID_HOMEPAGE);
      assertThat(store.getInstagramUrl()).isEqualTo(VALID_INSTAGRAM);
      assertThat(store.getXUrl()).isEqualTo(VALID_X_URL);
    }

    @Test
    @DisplayName("id, createdAt, updatedAt은 null로 생성된다 (DB에서 관리)")
    void create_timestampsAndIdAreNull() {
      // when
      TrainerStore store = createValidStore();

      // then
      assertThat(store.getId()).isNull();
      assertThat(store.getCreatedAt()).isNull();
      assertThat(store.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("선택 필드(detailAddress, phoneNumber, URL들)가 null이어도 생성된다")
    void create_withNullOptionalFields_succeeds() {
      // when
      TrainerStore store =
          TrainerStore.create(
              VALID_TRAINER_ID,
              VALID_STORE_NAME,
              VALID_ADDRESS,
              null, // detailAddress
              VALID_LATITUDE,
              VALID_LONGITUDE,
              null, // phoneNumber
              null, // homepageUrl
              null, // instagramUrl
              null); // xUrl

      // then
      assertThat(store.getDetailAddress()).isNull();
      assertThat(store.getPhoneNumber()).isNull();
      assertThat(store.getHomepageUrl()).isNull();
      assertThat(store.getInstagramUrl()).isNull();
      assertThat(store.getXUrl()).isNull();
    }

    @Test
    @DisplayName("위도 경계값(-90.0, 90.0)으로 생성된다")
    void create_withLatitudeBoundary_succeeds() {
      // when & then — no exception
      TrainerStore storeMin =
          TrainerStore.create(
              VALID_TRAINER_ID,
              VALID_STORE_NAME,
              VALID_ADDRESS,
              null,
              -90.0,
              VALID_LONGITUDE,
              null,
              null,
              null,
              null);
      TrainerStore storeMax =
          TrainerStore.create(
              VALID_TRAINER_ID,
              VALID_STORE_NAME,
              VALID_ADDRESS,
              null,
              90.0,
              VALID_LONGITUDE,
              null,
              null,
              null,
              null);

      assertThat(storeMin.getLatitude()).isEqualTo(-90.0);
      assertThat(storeMax.getLatitude()).isEqualTo(90.0);
    }

    @Test
    @DisplayName("경도 경계값(-180.0, 180.0)으로 생성된다")
    void create_withLongitudeBoundary_succeeds() {
      // when & then — no exception
      TrainerStore storeMin =
          TrainerStore.create(
              VALID_TRAINER_ID,
              VALID_STORE_NAME,
              VALID_ADDRESS,
              null,
              VALID_LATITUDE,
              -180.0,
              null,
              null,
              null,
              null);
      TrainerStore storeMax =
          TrainerStore.create(
              VALID_TRAINER_ID,
              VALID_STORE_NAME,
              VALID_ADDRESS,
              null,
              VALID_LATITUDE,
              180.0,
              null,
              null,
              null,
              null);

      assertThat(storeMin.getLongitude()).isEqualTo(-180.0);
      assertThat(storeMax.getLongitude()).isEqualTo(180.0);
    }
  }

  // ============================================
  // create() — trainerId 검증
  // ============================================

  @Nested
  @DisplayName("create() - trainerId 검증")
  class TrainerIdValidation {

    @Test
    @DisplayName("trainerId가 null이면 예외 발생")
    void create_withNullTrainerId_throwsException() {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      null,
                      VALID_STORE_NAME,
                      VALID_ADDRESS,
                      null,
                      VALID_LATITUDE,
                      VALID_LONGITUDE,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Trainer ID must be a positive number");
    }

    @Test
    @DisplayName("trainerId가 0이면 예외 발생")
    void create_withZeroTrainerId_throwsException() {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      0L,
                      VALID_STORE_NAME,
                      VALID_ADDRESS,
                      null,
                      VALID_LATITUDE,
                      VALID_LONGITUDE,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Trainer ID must be a positive number");
    }

    @Test
    @DisplayName("trainerId가 음수이면 예외 발생")
    void create_withNegativeTrainerId_throwsException() {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      -1L,
                      VALID_STORE_NAME,
                      VALID_ADDRESS,
                      null,
                      VALID_LATITUDE,
                      VALID_LONGITUDE,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Trainer ID must be a positive number");
    }
  }

  // ============================================
  // create() — storeName 검증
  // ============================================

  @Nested
  @DisplayName("create() - storeName 검증")
  class StoreNameValidation {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("storeName이 null 또는 공백이면 예외 발생")
    void create_withInvalidStoreName_throwsException(String storeName) {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID,
                      storeName,
                      VALID_ADDRESS,
                      null,
                      VALID_LATITUDE,
                      VALID_LONGITUDE,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Store name must not be null or blank");
    }
  }

  // ============================================
  // create() — address 검증
  // ============================================

  @Nested
  @DisplayName("create() - address 검증")
  class AddressValidation {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("address가 null 또는 공백이면 예외 발생")
    void create_withInvalidAddress_throwsException(String address) {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID,
                      VALID_STORE_NAME,
                      address,
                      null,
                      VALID_LATITUDE,
                      VALID_LONGITUDE,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Address must not be null or blank");
    }
  }

  // ============================================
  // create() — 좌표 검증
  // ============================================

  @Nested
  @DisplayName("create() - 좌표 검증")
  class CoordinateValidation {

    @Test
    @DisplayName("위도가 null이면 예외 발생")
    void create_withNullLatitude_throwsException() {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID,
                      VALID_STORE_NAME,
                      VALID_ADDRESS,
                      null,
                      null,
                      VALID_LONGITUDE,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Latitude");
    }

    @Test
    @DisplayName("경도가 null이면 예외 발생")
    void create_withNullLongitude_throwsException() {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID,
                      VALID_STORE_NAME,
                      VALID_ADDRESS,
                      null,
                      VALID_LATITUDE,
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Longitude");
    }

    @Test
    @DisplayName("위도가 범위를 초과하면 예외 발생 (> 90.0)")
    void create_withLatitudeAboveMax_throwsException() {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID,
                      VALID_STORE_NAME,
                      VALID_ADDRESS,
                      null,
                      90.1,
                      VALID_LONGITUDE,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Latitude must be between");
    }

    @Test
    @DisplayName("위도가 범위 미만이면 예외 발생 (< -90.0)")
    void create_withLatitudeBelowMin_throwsException() {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID,
                      VALID_STORE_NAME,
                      VALID_ADDRESS,
                      null,
                      -90.1,
                      VALID_LONGITUDE,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Latitude must be between");
    }

    @Test
    @DisplayName("경도가 범위를 초과하면 예외 발생 (> 180.0)")
    void create_withLongitudeAboveMax_throwsException() {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID,
                      VALID_STORE_NAME,
                      VALID_ADDRESS,
                      null,
                      VALID_LATITUDE,
                      180.1,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Longitude must be between");
    }

    @Test
    @DisplayName("경도가 범위 미만이면 예외 발생 (< -180.0)")
    void create_withLongitudeBelowMin_throwsException() {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID,
                      VALID_STORE_NAME,
                      VALID_ADDRESS,
                      null,
                      VALID_LATITUDE,
                      -180.1,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Longitude must be between");
    }
  }

  // ============================================
  // create() — URL 검증
  // ============================================

  @Nested
  @DisplayName("create() - URL 검증")
  class UrlValidation {

    @Test
    @DisplayName("유효한 https URL이 통과한다")
    void create_withValidHttpsUrl_succeeds() {
      TrainerStore store =
          TrainerStore.create(
              VALID_TRAINER_ID,
              VALID_STORE_NAME,
              VALID_ADDRESS,
              null,
              VALID_LATITUDE,
              VALID_LONGITUDE,
              null,
              "https://example.com",
              null,
              null);

      assertThat(store.getHomepageUrl()).isEqualTo("https://example.com");
    }

    @Test
    @DisplayName("유효한 http URL이 통과한다")
    void create_withValidHttpUrl_succeeds() {
      TrainerStore store =
          TrainerStore.create(
              VALID_TRAINER_ID,
              VALID_STORE_NAME,
              VALID_ADDRESS,
              null,
              VALID_LATITUDE,
              VALID_LONGITUDE,
              null,
              "http://example.com",
              null,
              null);

      assertThat(store.getHomepageUrl()).isEqualTo("http://example.com");
    }

    @Test
    @DisplayName("non-HTTP 스킴(ftp://)은 예외 발생")
    void create_withFtpScheme_throwsException() {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID,
                      VALID_STORE_NAME,
                      VALID_ADDRESS,
                      null,
                      VALID_LATITUDE,
                      VALID_LONGITUDE,
                      null,
                      "ftp://example.com/files",
                      null,
                      null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("must use http or https scheme");
    }

    @Test
    @DisplayName("file:// 스킴은 예외 발생")
    void create_withFileScheme_throwsException() {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID,
                      VALID_STORE_NAME,
                      VALID_ADDRESS,
                      null,
                      VALID_LATITUDE,
                      VALID_LONGITUDE,
                      null,
                      "file:///etc/passwd",
                      null,
                      null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("must use http or https scheme");
    }

    @Test
    @DisplayName("잘못된 형식의 URL은 예외 발생")
    void create_withMalformedUrl_throwsException() {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID,
                      VALID_STORE_NAME,
                      VALID_ADDRESS,
                      null,
                      VALID_LATITUDE,
                      VALID_LONGITUDE,
                      null,
                      "not a url at all",
                      null,
                      null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("must be a valid URL");
    }

    @Test
    @DisplayName("빈 문자열 URL은 허용된다 (optional field)")
    void create_withEmptyUrl_succeeds() {
      TrainerStore store =
          TrainerStore.create(
              VALID_TRAINER_ID,
              VALID_STORE_NAME,
              VALID_ADDRESS,
              null,
              VALID_LATITUDE,
              VALID_LONGITUDE,
              null,
              "",
              null,
              null);

      assertThat(store.getHomepageUrl()).isEmpty();
    }

    @Test
    @DisplayName("Instagram URL 검증도 동일하게 작동한다")
    void create_withInvalidInstagramUrl_throwsException() {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID,
                      VALID_STORE_NAME,
                      VALID_ADDRESS,
                      null,
                      VALID_LATITUDE,
                      VALID_LONGITUDE,
                      null,
                      null,
                      "ftp://invalid",
                      null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Instagram URL");
    }

    @Test
    @DisplayName("X URL 검증도 동일하게 작동한다")
    void create_withInvalidXUrl_throwsException() {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID,
                      VALID_STORE_NAME,
                      VALID_ADDRESS,
                      null,
                      VALID_LATITUDE,
                      VALID_LONGITUDE,
                      null,
                      null,
                      null,
                      "ftp://invalid"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("X URL");
    }
  }
}
