package momzzangseven.mztkbe.integration.e2e.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Marketplace Store E2E 테스트 (Local Server + Real PostgreSQL).
 *
 * <p>실행 조건:
 *
 * <ul>
 *   <li>로컬 PostgreSQL 서버 실행 필요 (application-integration.yml 참조)
 *   <li>./gradlew e2eTest 명령어로 실행
 * </ul>
 *
 * <p>테스트 시나리오:
 *
 * <ul>
 *   <li>스토어 신규 생성 (PUT) → 200 및 storeId 반환
 *   <li>스토어 조회 (GET) → 200 및 모든 필드 반환
 *   <li>스토어 업데이트 (PUT) → 200 및 동일 storeId 반환
 *   <li>존재하지 않는 스토어 조회 → 404 반환
 *   <li>필수 필드 누락 시 → 400 반환
 *   <li>인증 없이 접근 시 → 401 반환
 *   <li>다른 트레이너의 스토어에 접근 불가 (데이터 격리)
 * </ul>
 */
@DisplayName("[E2E] Marketplace Store 전체 흐름 테스트")
class MarketplaceStoreE2ETest extends E2ETestBase {

  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  private String baseUrl;
  private String accessToken;
  private String currentUserEmail;

  // ============================================================
  // Setup
  // ============================================================

  @BeforeEach
  void setUp() throws Exception {
    baseUrl = "http://localhost:" + port;
    currentUserEmail = uniqueEmail();
    signup(currentUserEmail, "Test@1234!", "마켓E2E유저");
    jdbcTemplate.update("UPDATE users SET role = 'TRAINER' WHERE email = ?", currentUserEmail);
    accessToken = loginAndGetAccessToken(currentUserEmail, "Test@1234!");
  }

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

  private Map<String, Object> createValidStoreBody() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("storeName", "PT Studio E2E");
    body.put("address", "서울시 강남구 역삼동 123");
    body.put("detailAddress", "2층 201호");
    body.put("latitude", 37.4979);
    body.put("longitude", 127.0276);
    body.put("phoneNumber", "010-1234-5678");
    body.put("homepageUrl", "https://example.com");
    body.put("instagramUrl", "https://instagram.com/test");
    body.put("xProfileUrl", "https://x.com/test");
    return body;
  }

  // ============================================================
  // E2E Tests — 스토어 생성 (PUT)
  // ============================================================

  @Test
  @DisplayName("[E-1] 스토어 신규 생성 → 200 및 storeId 반환")
  void upsertStore_createNew_returns200WithStoreId() throws Exception {
    Map<String, Object> body = createValidStoreBody();

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/marketplace/trainer/store",
            HttpMethod.PUT,
            new HttpEntity<>(body, authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data/storeId").asLong()).isPositive();
  }

  @Test
  @DisplayName("[E-2] 선택 필드(URL) 없이 스토어 생성 → 200 정상 처리")
  void upsertStore_withoutOptionalUrls_returns200() throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("storeName", "최소 필드 스토어");
    body.put("address", "서울시 강남구");
    body.put("detailAddress", "1층");
    body.put("latitude", 37.4979);
    body.put("longitude", 127.0276);
    body.put("phoneNumber", "010-9876-5432");

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/marketplace/trainer/store",
            HttpMethod.PUT,
            new HttpEntity<>(body, authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/data/storeId").asLong()).isPositive();
  }

  // ============================================================
  // E2E Tests — 스토어 조회 (GET)
  // ============================================================

  @Test
  @DisplayName("[E-3] 스토어 생성 후 조회 → 모든 필드가 올바르게 반환됨")
  void getStore_afterCreate_returnsAllFields() throws Exception {
    // given: 스토어 생성
    Map<String, Object> body = createValidStoreBody();
    restTemplate.exchange(
        baseUrl + "/marketplace/trainer/store",
        HttpMethod.PUT,
        new HttpEntity<>(body, authHeaders()),
        String.class);

    // when: 스토어 조회
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/marketplace/trainer/store",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders()),
            String.class);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data/storeId").asLong()).isPositive();
    assertThat(root.at("/data/storeName").asText()).isEqualTo("PT Studio E2E");
    assertThat(root.at("/data/address").asText()).isEqualTo("서울시 강남구 역삼동 123");
    assertThat(root.at("/data/detailAddress").asText()).isEqualTo("2층 201호");
    assertThat(root.at("/data/latitude").asDouble()).isEqualTo(37.4979);
    assertThat(root.at("/data/longitude").asDouble()).isEqualTo(127.0276);
    assertThat(root.at("/data/phoneNumber").asText()).isEqualTo("010-1234-5678");
    assertThat(root.at("/data/homepageUrl").asText()).isEqualTo("https://example.com");
    assertThat(root.at("/data/instagramUrl").asText()).isEqualTo("https://instagram.com/test");
    assertThat(root.at("/data/xProfileUrl").asText()).isEqualTo("https://x.com/test");
  }

  @Test
  @DisplayName("[E-4] 스토어가 없는 유저가 조회 시 404 반환")
  void getStore_noStore_returns404() {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/marketplace/trainer/store",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  // ============================================================
  // E2E Tests — 스토어 업데이트 (PUT)
  // ============================================================

  @Test
  @DisplayName("[E-5] 기존 스토어 업데이트 → 동일 storeId 유지, 필드 변경 확인")
  void upsertStore_updateExisting_retainsSameStoreId() throws Exception {
    // given: 스토어 생성
    Map<String, Object> createBody = createValidStoreBody();
    ResponseEntity<String> createResponse =
        restTemplate.exchange(
            baseUrl + "/marketplace/trainer/store",
            HttpMethod.PUT,
            new HttpEntity<>(createBody, authHeaders()),
            String.class);
    Long originalStoreId =
        objectMapper.readTree(createResponse.getBody()).at("/data/storeId").asLong();

    // when: 스토어 업데이트 (storeName 변경)
    Map<String, Object> updateBody = createValidStoreBody();
    updateBody.put("storeName", "Updated Studio E2E");
    updateBody.put("phoneNumber", "010-9999-8888");

    ResponseEntity<String> updateResponse =
        restTemplate.exchange(
            baseUrl + "/marketplace/trainer/store",
            HttpMethod.PUT,
            new HttpEntity<>(updateBody, authHeaders()),
            String.class);

    // then: 200 + 동일한 storeId
    assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    Long updatedStoreId =
        objectMapper.readTree(updateResponse.getBody()).at("/data/storeId").asLong();
    assertThat(updatedStoreId).isEqualTo(originalStoreId);

    // then: 조회 확인 — 업데이트된 값 반환
    ResponseEntity<String> getResponse =
        restTemplate.exchange(
            baseUrl + "/marketplace/trainer/store",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders()),
            String.class);
    JsonNode data = objectMapper.readTree(getResponse.getBody()).at("/data");
    assertThat(data.at("/storeName").asText()).isEqualTo("Updated Studio E2E");
    assertThat(data.at("/phoneNumber").asText()).isEqualTo("010-9999-8888");
  }

  // ============================================================
  // E2E Tests — 입력 검증 (400)
  // ============================================================

  @Test
  @DisplayName("[E-6] storeName 누락 시 400 반환")
  void upsertStore_missingStoreName_returns400() {
    Map<String, Object> body = createValidStoreBody();
    body.remove("storeName");

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/marketplace/trainer/store",
            HttpMethod.PUT,
            new HttpEntity<>(body, authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("[E-7] detailAddress 누락 시 400 반환")
  void upsertStore_missingDetailAddress_returns400() {
    Map<String, Object> body = createValidStoreBody();
    body.remove("detailAddress");

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/marketplace/trainer/store",
            HttpMethod.PUT,
            new HttpEntity<>(body, authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("[E-8] phoneNumber 누락 시 400 반환")
  void upsertStore_missingPhoneNumber_returns400() {
    Map<String, Object> body = createValidStoreBody();
    body.remove("phoneNumber");

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/marketplace/trainer/store",
            HttpMethod.PUT,
            new HttpEntity<>(body, authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("[E-9] 잘못된 전화번호 포맷 시 400 반환")
  void upsertStore_invalidPhoneFormat_returns400() {
    Map<String, Object> body = createValidStoreBody();
    body.put("phoneNumber", "abc-invalid");

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/marketplace/trainer/store",
            HttpMethod.PUT,
            new HttpEntity<>(body, authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("[E-10] latitude 누락 시 400 반환")
  void upsertStore_missingLatitude_returns400() {
    Map<String, Object> body = createValidStoreBody();
    body.remove("latitude");

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/marketplace/trainer/store",
            HttpMethod.PUT,
            new HttpEntity<>(body, authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("[E-11] 위도 범위 초과(-91.0) 시 400 반환")
  void upsertStore_latitudeOutOfRange_returns400() {
    Map<String, Object> body = createValidStoreBody();
    body.put("latitude", -91.0);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/marketplace/trainer/store",
            HttpMethod.PUT,
            new HttpEntity<>(body, authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  // ============================================================
  // E2E Tests — 인증 (401)
  // ============================================================

  @Test
  @DisplayName("[E-12] 인증 없이 스토어 생성 시 401 반환")
  void upsertStore_withoutAuth_returns401() {
    HttpHeaders noAuthHeaders = new HttpHeaders();
    noAuthHeaders.setContentType(MediaType.APPLICATION_JSON);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/marketplace/trainer/store",
            HttpMethod.PUT,
            new HttpEntity<>(createValidStoreBody(), noAuthHeaders),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("[E-13] 인증 없이 스토어 조회 시 401 반환")
  void getStore_withoutAuth_returns401() {
    HttpHeaders noAuthHeaders = new HttpHeaders();
    noAuthHeaders.setContentType(MediaType.APPLICATION_JSON);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/marketplace/trainer/store",
            HttpMethod.GET,
            new HttpEntity<>(noAuthHeaders),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  // ============================================================
  // E2E Tests — 트레이너 간 데이터 격리
  // ============================================================

  @Test
  @DisplayName("[E-14] 다른 트레이너의 스토어는 보이지 않는다 (데이터 격리)")
  void getStore_otherTrainerStore_notVisible() throws Exception {
    // given: 현재 유저가 스토어 생성
    restTemplate.exchange(
        baseUrl + "/marketplace/trainer/store",
        HttpMethod.PUT,
        new HttpEntity<>(createValidStoreBody(), authHeaders()),
        String.class);

    // when: 다른 유저로 로그인 후 스토어 조회
    String otherEmail = uniqueEmail();
    signup(otherEmail, "Test@1234!", "다른유저");
    jdbcTemplate.update("UPDATE users SET role = 'TRAINER' WHERE email = ?", otherEmail);
    String otherToken = loginAndGetAccessToken(otherEmail, "Test@1234!");

    HttpHeaders otherHeaders = new HttpHeaders();
    otherHeaders.setContentType(MediaType.APPLICATION_JSON);
    otherHeaders.setBearerAuth(otherToken);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/marketplace/trainer/store",
            HttpMethod.GET,
            new HttpEntity<>(otherHeaders),
            String.class);

    // then: 다른 유저에게는 스토어가 없으므로 404
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
