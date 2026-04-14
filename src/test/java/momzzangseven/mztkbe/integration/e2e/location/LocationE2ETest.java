package momzzangseven.mztkbe.integration.e2e.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.location.application.dto.AddressInfo;
import momzzangseven.mztkbe.modules.location.application.dto.CoordinatesInfo;
import momzzangseven.mztkbe.modules.location.application.port.out.GeocodingPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Location E2E 테스트 (Local Server + Real PostgreSQL).
 *
 * <p>외부 Kakao Geocoding API는 {@code @MockitoBean GeocodingPort}로 대체합니다.
 */
@DisplayName("[E2E] Location 전체 흐름 테스트")
class LocationE2ETest extends E2ETestBase {

  /** 테스트에 사용할 고정 좌표 (서울시청 인근). */
  private static final double TEST_LATITUDE = 37.5665;

  private static final double TEST_LONGITUDE = 126.978;
  private static final String TEST_ADDRESS = "서울특별시 중구 세종대로 110";
  private static final String TEST_POSTAL_CODE = "04524";

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private GeocodingPort geocodingPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  private String baseUrl;
  private String accessToken;

  // ============================================================
  // Helper Methods
  // ============================================================

  private static String uniqueEmail() {
    return "e2e-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10) + "@example.com";
  }

  private HttpHeaders authHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(accessToken);
    return headers;
  }

  private void signup(String email, String password, String nickname) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    Map<String, String> body = Map.of("email", email, "password", password, "nickname", nickname);
    restTemplate.exchange(
        baseUrl + "/auth/signup", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
  }

  private String loginAndGetAccessToken(String email, String password) throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    Map<String, Object> body = Map.of("provider", "LOCAL", "email", email, "password", password);
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            String.class);
    return objectMapper.readTree(response.getBody()).at("/data/accessToken").asText();
  }

  private Long registerLocationWithCoordinates(String locationName) throws Exception {
    Map<String, Object> body =
        Map.of(
            "locationName", locationName,
            "latitude", TEST_LATITUDE,
            "longitude", TEST_LONGITUDE);
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/users/me/locations/register",
            HttpMethod.POST,
            new HttpEntity<>(body, authHeaders()),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return objectMapper.readTree(response.getBody()).at("/data/locationId").asLong();
  }

  // ============================================================
  // Setup
  // ============================================================

  @BeforeEach
  void setUp() throws Exception {
    baseUrl = "http://localhost:" + port;

    given(geocodingPort.geocode(anyString()))
        .willReturn(CoordinatesInfo.of(TEST_LATITUDE, TEST_LONGITUDE));
    given(geocodingPort.reverseGeocode(anyDouble(), anyDouble()))
        .willReturn(AddressInfo.of(TEST_ADDRESS, TEST_POSTAL_CODE));

    String email = uniqueEmail();
    signup(email, "Test@1234!", "위치E2E유저");
    accessToken = loginAndGetAccessToken(email, "Test@1234!");
  }

  // ============================================================
  // E2E Tests
  // ============================================================

  @Test
  @DisplayName("좌표 기반 위치 등록 → locationId 반환 및 DB 저장 확인")
  void registerLocation_withCoordinates_success() throws Exception {
    Map<String, Object> body =
        Map.of(
            "locationName", "서울시청 E2E 테스트",
            "latitude", TEST_LATITUDE,
            "longitude", TEST_LONGITUDE);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/users/me/locations/register",
            HttpMethod.POST,
            new HttpEntity<>(body, authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data/locationId").asLong()).isPositive();
    assertThat(root.at("/data/locationName").asText()).isEqualTo("서울시청 E2E 테스트");
    assertThat(root.at("/data/latitude").asDouble()).isEqualTo(TEST_LATITUDE);
    assertThat(root.at("/data/longitude").asDouble()).isEqualTo(TEST_LONGITUDE);
  }

  @Test
  @DisplayName("주소 기반 위치 등록 → GeocodingPort 호출로 좌표 변환 후 저장")
  void registerLocation_withAddress_geocodedAndSaved() throws Exception {
    Map<String, Object> body =
        Map.of(
            "locationName", "주소 기반 위치",
            "address", TEST_ADDRESS,
            "postalCode", TEST_POSTAL_CODE);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/users/me/locations/register",
            HttpMethod.POST,
            new HttpEntity<>(body, authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/data/locationId").asLong()).isPositive();
    assertThat(root.at("/data/latitude").asDouble()).isEqualTo(TEST_LATITUDE);
    assertThat(root.at("/data/longitude").asDouble()).isEqualTo(TEST_LONGITUDE);
  }

  @Test
  @DisplayName("내 위치 목록 조회 → 등록된 위치가 목록에 포함")
  void getMyLocations_afterRegister_includesLocation() throws Exception {
    registerLocationWithCoordinates("조회 테스트 위치");

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/users/me/locations",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data/totalCount").asInt()).isGreaterThanOrEqualTo(1);
    assertThat(root.at("/data/locations").isArray()).isTrue();
  }

  @Test
  @DisplayName("동일 좌표로 위치 인증 → isVerified=true 반환")
  void verifyLocation_sameCoordinates_returnsVerified() throws Exception {
    Long locationId = registerLocationWithCoordinates("인증 테스트 위치");

    Map<String, Object> verifyBody =
        Map.of(
            "locationId", locationId,
            "currentLatitude", TEST_LATITUDE,
            "currentLongitude", TEST_LONGITUDE);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/locations/verify",
            HttpMethod.POST,
            new HttpEntity<>(verifyBody, authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data/isVerified").asBoolean()).isTrue();
    assertThat(root.at("/data/locationId").asLong()).isEqualTo(locationId);
  }

  @Test
  @DisplayName("멀리 떨어진 좌표로 위치 인증 → isVerified=false 반환")
  void verifyLocation_farCoordinates_returnsNotVerified() throws Exception {
    Long locationId = registerLocationWithCoordinates("원거리 인증 테스트");

    Map<String, Object> verifyBody =
        Map.of(
            "locationId", locationId,
            "currentLatitude", 35.1796,
            "currentLongitude", 129.0756);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/locations/verify",
            HttpMethod.POST,
            new HttpEntity<>(verifyBody, authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/data/isVerified").asBoolean()).isFalse();
  }

  @Test
  @DisplayName("위치 삭제 → 삭제 후 200 응답 및 locationId 반환")
  void deleteLocation_success_returns200WithLocationId() throws Exception {
    Long locationId = registerLocationWithCoordinates("삭제될 위치");

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/users/me/locations/" + locationId,
            HttpMethod.DELETE,
            new HttpEntity<>(authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data/locationId").asLong()).isEqualTo(locationId);
  }

  @Test
  @DisplayName("위치 이름 없이 등록 시 400 반환")
  void registerLocation_withoutName_returns400() {
    Map<String, Object> body = Map.of("latitude", TEST_LATITUDE, "longitude", TEST_LONGITUDE);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/users/me/locations/register",
            HttpMethod.POST,
            new HttpEntity<>(body, authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("위치 정보 없이 등록 시 (주소도 좌표도 없음) → 에러 반환")
  void registerLocation_withoutCoordinatesOrAddress_returnsError() {
    Map<String, Object> body = Map.of("locationName", "정보 없는 위치");

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/users/me/locations/register",
            HttpMethod.POST,
            new HttpEntity<>(body, authHeaders()),
            String.class);

    assertThat(response.getStatusCode().is4xxClientError()).as("좌표도 주소도 없으면 에러여야 함").isTrue();
  }

  @Test
  @DisplayName("인증 없이 위치 등록 시 401 반환")
  void registerLocation_withoutAuth_returns401() {
    HttpHeaders noAuthHeaders = new HttpHeaders();
    noAuthHeaders.setContentType(MediaType.APPLICATION_JSON);
    Map<String, Object> body =
        Map.of("locationName", "비인증 위치", "latitude", TEST_LATITUDE, "longitude", TEST_LONGITUDE);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/users/me/locations/register",
            HttpMethod.POST,
            new HttpEntity<>(body, noAuthHeaders),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
