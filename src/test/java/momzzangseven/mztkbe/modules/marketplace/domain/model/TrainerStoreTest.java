package momzzangseven.mztkbe.modules.marketplace.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidCoordinatesException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidPhoneNumberException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidStoreAddressException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidStoreNameException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidStoreUrlException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidTrainerIdException;
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
  private static final String VALID_X_PROFILE_URL = "https://x.com/trainer";

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
        VALID_X_PROFILE_URL);
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
      TrainerStore store = createValidStore();

      assertThat(store.getTrainerId()).isEqualTo(VALID_TRAINER_ID);
      assertThat(store.getStoreName()).isEqualTo(VALID_STORE_NAME);
      assertThat(store.getAddress()).isEqualTo(VALID_ADDRESS);
      assertThat(store.getDetailAddress()).isEqualTo(VALID_DETAIL_ADDRESS);
      assertThat(store.getLatitude()).isEqualTo(VALID_LATITUDE);
      assertThat(store.getLongitude()).isEqualTo(VALID_LONGITUDE);
      assertThat(store.getPhoneNumber()).isEqualTo(VALID_PHONE);
      assertThat(store.getHomepageUrl()).isEqualTo(VALID_HOMEPAGE);
      assertThat(store.getInstagramUrl()).isEqualTo(VALID_INSTAGRAM);
      assertThat(store.getXProfileUrl()).isEqualTo(VALID_X_PROFILE_URL);
    }

    @Test
    @DisplayName("id, createdAt, updatedAt은 null로 생성된다 (DB에서 관리)")
    void create_timestampsAndIdAreNull() {
      TrainerStore store = createValidStore();

      assertThat(store.getId()).isNull();
      assertThat(store.getCreatedAt()).isNull();
      assertThat(store.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("선택 필드(URL들)가 null이어도 생성된다")
    void create_withNullOptionalFields_succeeds() {
      TrainerStore store =
          TrainerStore.create(
              VALID_TRAINER_ID,
              VALID_STORE_NAME,
              VALID_ADDRESS,
              VALID_DETAIL_ADDRESS,
              VALID_LATITUDE,
              VALID_LONGITUDE,
              VALID_PHONE,
              null,
              null,
              null);

      assertThat(store.getHomepageUrl()).isNull();
      assertThat(store.getInstagramUrl()).isNull();
      assertThat(store.getXProfileUrl()).isNull();
    }

    @Test
    @DisplayName("위도 경계값(-90.0, 90.0)으로 생성된다")
    void create_withLatitudeBoundary_succeeds() {
      TrainerStore storeMin =
          TrainerStore.create(
              VALID_TRAINER_ID, VALID_STORE_NAME, VALID_ADDRESS, VALID_DETAIL_ADDRESS,
              -90.0, VALID_LONGITUDE, VALID_PHONE, null, null, null);
      TrainerStore storeMax =
          TrainerStore.create(
              VALID_TRAINER_ID, VALID_STORE_NAME, VALID_ADDRESS, VALID_DETAIL_ADDRESS,
              90.0, VALID_LONGITUDE, VALID_PHONE, null, null, null);

      assertThat(storeMin.getLatitude()).isEqualTo(-90.0);
      assertThat(storeMax.getLatitude()).isEqualTo(90.0);
    }

    @Test
    @DisplayName("경도 경계값(-180.0, 180.0)으로 생성된다")
    void create_withLongitudeBoundary_succeeds() {
      TrainerStore storeMin =
          TrainerStore.create(
              VALID_TRAINER_ID, VALID_STORE_NAME, VALID_ADDRESS, VALID_DETAIL_ADDRESS,
              VALID_LATITUDE, -180.0, VALID_PHONE, null, null, null);
      TrainerStore storeMax =
          TrainerStore.create(
              VALID_TRAINER_ID, VALID_STORE_NAME, VALID_ADDRESS, VALID_DETAIL_ADDRESS,
              VALID_LATITUDE, 180.0, VALID_PHONE, null, null, null);

      assertThat(storeMin.getLongitude()).isEqualTo(-180.0);
      assertThat(storeMax.getLongitude()).isEqualTo(180.0);
    }

    @Test
    @DisplayName("update()로 기존 ID/trainerId를 유지하면서 필드를 변경할 수 있다")
    void update_preservesIdentityAndUpdatesFields() {
      TrainerStore original =
          TrainerStore.builder()
              .id(100L)
              .trainerId(VALID_TRAINER_ID)
              .storeName(VALID_STORE_NAME)
              .address(VALID_ADDRESS)
              .detailAddress(VALID_DETAIL_ADDRESS)
              .latitude(VALID_LATITUDE)
              .longitude(VALID_LONGITUDE)
              .phoneNumber(VALID_PHONE)
              .homepageUrl(VALID_HOMEPAGE)
              .instagramUrl(VALID_INSTAGRAM)
              .xProfileUrl(VALID_X_PROFILE_URL)
              .build();

      TrainerStore updated =
          original.update(
              "New Studio",
              "서울시 서초구",
              "3층 301호",
              37.5000,
              127.0300,
              "02-1234-5678",
              "https://newsite.com",
              null,
              null);

      // identity preserved
      assertThat(updated.getId()).isEqualTo(100L);
      assertThat(updated.getTrainerId()).isEqualTo(VALID_TRAINER_ID);
      // fields updated
      assertThat(updated.getStoreName()).isEqualTo("New Studio");
      assertThat(updated.getAddress()).isEqualTo("서울시 서초구");
      assertThat(updated.getDetailAddress()).isEqualTo("3층 301호");
      assertThat(updated.getLatitude()).isEqualTo(37.5000);
      assertThat(updated.getLongitude()).isEqualTo(127.0300);
      assertThat(updated.getPhoneNumber()).isEqualTo("02-1234-5678");
      assertThat(updated.getHomepageUrl()).isEqualTo("https://newsite.com");
      assertThat(updated.getInstagramUrl()).isNull();
      assertThat(updated.getXProfileUrl()).isNull();
      // original immutable
      assertThat(original.getStoreName()).isEqualTo(VALID_STORE_NAME);
    }

    @Test
    @DisplayName("update()에서 잘못된 값이 들어오면 BusinessException 계열 예외가 발생한다")
    void update_withInvalidValues_throwsBusinessException() {
      TrainerStore original = createValidStore();

      assertThatThrownBy(
              () ->
                  original.update(
                      "",
                      VALID_ADDRESS,
                      VALID_DETAIL_ADDRESS,
                      VALID_LATITUDE,
                      VALID_LONGITUDE,
                      VALID_PHONE,
                      null,
                      null,
                      null))
          .isInstanceOf(MarketplaceInvalidStoreNameException.class)
          .hasMessageContaining("Store name must not be null or blank");
    }
  }

  // ============================================
  // create() — trainerId 검증
  // ============================================

  @Nested
  @DisplayName("create() - trainerId 검증")
  class TrainerIdValidation {

    @Test
    @DisplayName("trainerId가 null이면 MarketplaceInvalidTrainerIdException 발생")
    void create_withNullTrainerId_throwsException() {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      null, VALID_STORE_NAME, VALID_ADDRESS, VALID_DETAIL_ADDRESS,
                      VALID_LATITUDE, VALID_LONGITUDE, VALID_PHONE, null, null, null))
          .isInstanceOf(MarketplaceInvalidTrainerIdException.class);
    }

    @Test
    @DisplayName("trainerId가 0이면 MarketplaceInvalidTrainerIdException 발생")
    void create_withZeroTrainerId_throwsException() {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      0L, VALID_STORE_NAME, VALID_ADDRESS, VALID_DETAIL_ADDRESS,
                      VALID_LATITUDE, VALID_LONGITUDE, VALID_PHONE, null, null, null))
          .isInstanceOf(MarketplaceInvalidTrainerIdException.class);
    }

    @Test
    @DisplayName("trainerId가 음수이면 MarketplaceInvalidTrainerIdException 발생")
    void create_withNegativeTrainerId_throwsException() {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      -1L, VALID_STORE_NAME, VALID_ADDRESS, VALID_DETAIL_ADDRESS,
                      VALID_LATITUDE, VALID_LONGITUDE, VALID_PHONE, null, null, null))
          .isInstanceOf(MarketplaceInvalidTrainerIdException.class);
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
    @DisplayName("storeName이 null 또는 공백이면 MarketplaceInvalidStoreNameException 발생")
    void create_withInvalidStoreName_throwsException(String storeName) {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID, storeName, VALID_ADDRESS, VALID_DETAIL_ADDRESS,
                      VALID_LATITUDE, VALID_LONGITUDE, VALID_PHONE, null, null, null))
          .isInstanceOf(MarketplaceInvalidStoreNameException.class)
          .hasMessageContaining("Store name must not be null or blank");
    }

    @Test
    @DisplayName("storeName이 최대 길이를 초과하면 MarketplaceInvalidStoreNameException 발생")
    void create_withTooLongStoreName_throwsException() {
      String longName = "A".repeat(TrainerStore.MAX_STORE_NAME_LENGTH + 1);
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID, longName, VALID_ADDRESS, VALID_DETAIL_ADDRESS,
                      VALID_LATITUDE, VALID_LONGITUDE, VALID_PHONE, null, null, null))
          .isInstanceOf(MarketplaceInvalidStoreNameException.class)
          .hasMessageContaining("must not exceed");
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
    @DisplayName("address가 null 또는 공백이면 MarketplaceInvalidStoreAddressException 발생")
    void create_withInvalidAddress_throwsException(String address) {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID, VALID_STORE_NAME, address, VALID_DETAIL_ADDRESS,
                      VALID_LATITUDE, VALID_LONGITUDE, VALID_PHONE, null, null, null))
          .isInstanceOf(MarketplaceInvalidStoreAddressException.class)
          .hasMessageContaining("Address must not be null or blank");
    }

    @Test
    @DisplayName("address가 최대 길이를 초과하면 MarketplaceInvalidStoreAddressException 발생")
    void create_withTooLongAddress_throwsException() {
      String longAddress = "A".repeat(TrainerStore.MAX_ADDRESS_LENGTH + 1);
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID, VALID_STORE_NAME, longAddress, VALID_DETAIL_ADDRESS,
                      VALID_LATITUDE, VALID_LONGITUDE, VALID_PHONE, null, null, null))
          .isInstanceOf(MarketplaceInvalidStoreAddressException.class)
          .hasMessageContaining("must not exceed");
    }
  }

  // ============================================
  // create() — detailAddress 검증
  // ============================================

  @Nested
  @DisplayName("create() - detailAddress 검증")
  class DetailAddressValidation {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("detailAddress가 null 또는 공백이면 MarketplaceInvalidStoreAddressException 발생")
    void create_withInvalidDetailAddress_throwsException(String detailAddress) {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID, VALID_STORE_NAME, VALID_ADDRESS, detailAddress,
                      VALID_LATITUDE, VALID_LONGITUDE, VALID_PHONE, null, null, null))
          .isInstanceOf(MarketplaceInvalidStoreAddressException.class)
          .hasMessageContaining("Detail address must not be null or blank");
    }

    @Test
    @DisplayName("detailAddress가 최대 길이를 초과하면 MarketplaceInvalidStoreAddressException 발생")
    void create_withTooLongDetailAddress_throwsException() {
      String longDetail = "A".repeat(TrainerStore.MAX_DETAIL_ADDRESS_LENGTH + 1);
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID, VALID_STORE_NAME, VALID_ADDRESS, longDetail,
                      VALID_LATITUDE, VALID_LONGITUDE, VALID_PHONE, null, null, null))
          .isInstanceOf(MarketplaceInvalidStoreAddressException.class)
          .hasMessageContaining("must not exceed");
    }
  }

  // ============================================
  // create() — 좌표 검증
  // ============================================

  @Nested
  @DisplayName("create() - 좌표 검증")
  class CoordinateValidation {

    @Test
    @DisplayName("위도가 null이면 MarketplaceInvalidCoordinatesException 발생")
    void create_withNullLatitude_throwsException() {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID, VALID_STORE_NAME, VALID_ADDRESS, VALID_DETAIL_ADDRESS,
                      null, VALID_LONGITUDE, VALID_PHONE, null, null, null))
          .isInstanceOf(MarketplaceInvalidCoordinatesException.class)
          .hasMessageContaining("Latitude");
    }

    @Test
    @DisplayName("경도가 null이면 MarketplaceInvalidCoordinatesException 발생")
    void create_withNullLongitude_throwsException() {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID, VALID_STORE_NAME, VALID_ADDRESS, VALID_DETAIL_ADDRESS,
                      VALID_LATITUDE, null, VALID_PHONE, null, null, null))
          .isInstanceOf(MarketplaceInvalidCoordinatesException.class)
          .hasMessageContaining("Longitude");
    }

    @Test
    @DisplayName("위도가 90.1이면 MarketplaceInvalidCoordinatesException 발생")
    void create_withLatitudeAboveMax_throwsException() {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID, VALID_STORE_NAME, VALID_ADDRESS, VALID_DETAIL_ADDRESS,
                      90.1, VALID_LONGITUDE, VALID_PHONE, null, null, null))
          .isInstanceOf(MarketplaceInvalidCoordinatesException.class)
          .hasMessageContaining("Latitude must be between");
    }

    @Test
    @DisplayName("위도가 -90.1이면 MarketplaceInvalidCoordinatesException 발생")
    void create_withLatitudeBelowMin_throwsException() {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID, VALID_STORE_NAME, VALID_ADDRESS, VALID_DETAIL_ADDRESS,
                      -90.1, VALID_LONGITUDE, VALID_PHONE, null, null, null))
          .isInstanceOf(MarketplaceInvalidCoordinatesException.class)
          .hasMessageContaining("Latitude must be between");
    }

    @Test
    @DisplayName("경도가 180.1이면 MarketplaceInvalidCoordinatesException 발생")
    void create_withLongitudeAboveMax_throwsException() {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID, VALID_STORE_NAME, VALID_ADDRESS, VALID_DETAIL_ADDRESS,
                      VALID_LATITUDE, 180.1, VALID_PHONE, null, null, null))
          .isInstanceOf(MarketplaceInvalidCoordinatesException.class)
          .hasMessageContaining("Longitude must be between");
    }

    @Test
    @DisplayName("경도가 -180.1이면 MarketplaceInvalidCoordinatesException 발생")
    void create_withLongitudeBelowMin_throwsException() {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID, VALID_STORE_NAME, VALID_ADDRESS, VALID_DETAIL_ADDRESS,
                      VALID_LATITUDE, -180.1, VALID_PHONE, null, null, null))
          .isInstanceOf(MarketplaceInvalidCoordinatesException.class)
          .hasMessageContaining("Longitude must be between");
    }
  }

  // ============================================
  // create() — phoneNumber 검증
  // ============================================

  @Nested
  @DisplayName("create() - phoneNumber 검증")
  class PhoneNumberValidation {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("phoneNumber가 null 또는 공백이면 MarketplaceInvalidPhoneNumberException 발생")
    void create_withInvalidPhoneNumber_throwsException(String phoneNumber) {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID, VALID_STORE_NAME, VALID_ADDRESS, VALID_DETAIL_ADDRESS,
                      VALID_LATITUDE, VALID_LONGITUDE, phoneNumber, null, null, null))
          .isInstanceOf(MarketplaceInvalidPhoneNumberException.class)
          .hasMessageContaining("Phone number must not be null or blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {"010-1234-5678", "02-1234-5678", "+82-10-1234-5678", "01012345678"})
    @DisplayName("유효한 전화번호 포맷이 통과한다")
    void create_withValidPhoneFormats_succeeds(String phone) {
      TrainerStore store =
          TrainerStore.create(
              VALID_TRAINER_ID, VALID_STORE_NAME, VALID_ADDRESS, VALID_DETAIL_ADDRESS,
              VALID_LATITUDE, VALID_LONGITUDE, phone, null, null, null);
      assertThat(store.getPhoneNumber()).isEqualTo(phone);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "12", "phone-number", "!@#$%"})
    @DisplayName("잘못된 전화번호 포맷이면 MarketplaceInvalidPhoneNumberException 발생")
    void create_withInvalidPhoneFormat_throwsException(String phone) {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID, VALID_STORE_NAME, VALID_ADDRESS, VALID_DETAIL_ADDRESS,
                      VALID_LATITUDE, VALID_LONGITUDE, phone, null, null, null))
          .isInstanceOf(MarketplaceInvalidPhoneNumberException.class)
          .hasMessageContaining("Phone number must be a valid format");
    }

    @Test
    @DisplayName("phoneNumber가 최대 길이를 초과하면 MarketplaceInvalidPhoneNumberException 발생")
    void create_withTooLongPhoneNumber_throwsException() {
      String longPhone = "+82-10-1234-5678-9012345";
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID, VALID_STORE_NAME, VALID_ADDRESS, VALID_DETAIL_ADDRESS,
                      VALID_LATITUDE, VALID_LONGITUDE, longPhone, null, null, null))
          .isInstanceOf(MarketplaceInvalidPhoneNumberException.class);
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
              VALID_TRAINER_ID, VALID_STORE_NAME, VALID_ADDRESS, VALID_DETAIL_ADDRESS,
              VALID_LATITUDE, VALID_LONGITUDE, VALID_PHONE, "https://example.com", null, null);

      assertThat(store.getHomepageUrl()).isEqualTo("https://example.com");
    }

    @Test
    @DisplayName("유효한 http URL이 통과한다")
    void create_withValidHttpUrl_succeeds() {
      TrainerStore store =
          TrainerStore.create(
              VALID_TRAINER_ID, VALID_STORE_NAME, VALID_ADDRESS, VALID_DETAIL_ADDRESS,
              VALID_LATITUDE, VALID_LONGITUDE, VALID_PHONE, "http://example.com", null, null);

      assertThat(store.getHomepageUrl()).isEqualTo("http://example.com");
    }

    @Test
    @DisplayName("non-HTTP 스킴(ftp://)은 MarketplaceInvalidStoreUrlException 발생")
    void create_withFtpScheme_throwsException() {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID, VALID_STORE_NAME, VALID_ADDRESS, VALID_DETAIL_ADDRESS,
                      VALID_LATITUDE, VALID_LONGITUDE, VALID_PHONE,
                      "ftp://example.com/files", null, null))
          .isInstanceOf(MarketplaceInvalidStoreUrlException.class)
          .hasMessageContaining("must use http or https scheme");
    }

    @Test
    @DisplayName("file:// 스킴은 MarketplaceInvalidStoreUrlException 발생")
    void create_withFileScheme_throwsException() {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID, VALID_STORE_NAME, VALID_ADDRESS, VALID_DETAIL_ADDRESS,
                      VALID_LATITUDE, VALID_LONGITUDE, VALID_PHONE,
                      "file:///etc/passwd", null, null))
          .isInstanceOf(MarketplaceInvalidStoreUrlException.class)
          .hasMessageContaining("must use http or https scheme");
    }

    @Test
    @DisplayName("잘못된 형식의 URL은 MarketplaceInvalidStoreUrlException 발생")
    void create_withMalformedUrl_throwsException() {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID, VALID_STORE_NAME, VALID_ADDRESS, VALID_DETAIL_ADDRESS,
                      VALID_LATITUDE, VALID_LONGITUDE, VALID_PHONE,
                      "not a url at all", null, null))
          .isInstanceOf(MarketplaceInvalidStoreUrlException.class)
          .hasMessageContaining("must be a valid URL");
    }

    @Test
    @DisplayName("Instagram URL 검증도 MarketplaceInvalidStoreUrlException로 동일하게 작동한다")
    void create_withInvalidInstagramUrl_throwsException() {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID, VALID_STORE_NAME, VALID_ADDRESS, VALID_DETAIL_ADDRESS,
                      VALID_LATITUDE, VALID_LONGITUDE, VALID_PHONE,
                      null, "ftp://bad-instagram-url", null))
          .isInstanceOf(MarketplaceInvalidStoreUrlException.class);
    }

    @Test
    @DisplayName("X Profile URL 검증도 MarketplaceInvalidStoreUrlException로 동일하게 작동한다")
    void create_withInvalidXProfileUrl_throwsException() {
      assertThatThrownBy(
              () ->
                  TrainerStore.create(
                      VALID_TRAINER_ID, VALID_STORE_NAME, VALID_ADDRESS, VALID_DETAIL_ADDRESS,
                      VALID_LATITUDE, VALID_LONGITUDE, VALID_PHONE,
                      null, null, "not-a-url"))
          .isInstanceOf(MarketplaceInvalidStoreUrlException.class);
    }
  }
}
