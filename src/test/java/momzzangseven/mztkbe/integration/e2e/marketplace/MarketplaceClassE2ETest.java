package momzzangseven.mztkbe.integration.e2e.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * Marketplace Class E2E 테스트 (Local Server + Real PostgreSQL).
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
 *   <li>클래스 신규 등록 (POST) → 201 및 classId 반환
 *   <li>클래스 상세 조회 (GET) → 200 및 모든 필드 반환
 *   <li>클래스 수정 (PUT) → 200 및 동일 classId 반환
 *   <li>클래스 상태 토글 (PATCH) → 200 및 new active 상태
 *   <li>트레이너 자신의 클래스 목록 (GET /trainer/classes) → 200 및 등록된 클래스 포함
 *   <li>공개 클래스 목록 (GET /classes) → 200 반환
 *   <li>스토어 미등록 트레이너 클래스 등록 시도 → 404
 *   <li>제재 중인 트레이너 클래스 등록 시도 → 403
 *   <li>다른 트레이너 클래스 수정 시도 → 403 (권한 없음)
 *   <li>필수 필드 누락 시 → 400 반환
 *   <li>인증 없이 접근 시 → 401 반환
 * </ul>
 */
@DisplayName("[E2E] Marketplace Class 전체 흐름 테스트")
class MarketplaceClassE2ETest extends E2ETestBase {

  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  private String trainerAccessToken;
  private String trainerEmail;

  // ============================================================
  // Setup — 트레이너 계정 + 스토어 사전 등록
  // ============================================================

  @BeforeEach
  void setUp() throws Exception {
    trainerEmail = randomEmail();
    Long trainerId = signupUser(trainerEmail, DEFAULT_TEST_PASSWORD, "클래스E2E트레이너");

    // TRAINER 롤 부여
    jdbcTemplate.update("UPDATE users SET role = 'TRAINER' WHERE email = ?", trainerEmail);
    trainerAccessToken = loginUser(trainerEmail, DEFAULT_TEST_PASSWORD);

    // 스토어 등록 (클래스 등록을 위한 사전 조건)
    upsertStore(trainerAccessToken);
  }

  // ============================================================
  // Helper Methods
  // ============================================================

  private HttpHeaders authHeaders(String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(accessToken);
    return headers;
  }

  private void upsertStore(String accessToken) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("storeName", "PT Studio 클래스E2E");
    body.put("address", "서울시 강남구 역삼동 123");
    body.put("detailAddress", "2층 201호");
    body.put("latitude", 37.4979);
    body.put("longitude", 127.0276);
    body.put("phoneNumber", "010-1234-5678");
    restTemplate.exchange(
        baseUrl() + "/marketplace/trainer/store",
        HttpMethod.PUT,
        new HttpEntity<>(body, authHeaders(accessToken)),
        String.class);
  }

  private Map<String, Object> validClassBody() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("title", "PT 60분 기초체력");
    body.put("category", "PT");
    body.put("description", "기초 체력 향상을 위한 PT 클래스입니다.");
    body.put("priceAmount", 50000);
    body.put("durationMinutes", 60);
    body.put("tags", List.of("다이어트", "근력강화"));
    body.put("features", List.of("1:1 맞춤 프로그램", "체력 측정 포함"));
    body.put(
        "classTimes",
        List.of(
            Map.of(
                "daysOfWeek",
                List.of("MONDAY", "WEDNESDAY"),
                "startTime",
                "10:00:00",
                "capacity",
                5)));
    return body;
  }

  private Long registerClassAndGetId(String accessToken) throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/marketplace/trainer/classes",
            HttpMethod.POST,
            new HttpEntity<>(validClassBody(), authHeaders(accessToken)),
            String.class);
    return objectMapper.readTree(response.getBody()).at("/data/classId").asLong();
  }

  // ============================================================
  // E2E Tests — 클래스 등록 (POST)
  // ============================================================

  @Test
  @DisplayName("[CE-1] 스토어가 있는 트레이너가 유효한 클래스 등록 시 201 및 classId 반환")
  void registerClass_validRequest_returns201WithClassId() throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/marketplace/trainer/classes",
            HttpMethod.POST,
            new HttpEntity<>(validClassBody(), authHeaders(trainerAccessToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data/classId").asLong()).isPositive();
  }

  @Test
  @DisplayName("[CE-2] 태그/피처 없이 클래스 등록 시 201 정상 처리")
  void registerClass_withoutOptionalFields_returns201() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("title", "최소 필드 PT 클래스");
    body.put("category", "YOGA");
    body.put("description", "요가 클래스 설명");
    body.put("priceAmount", 30000);
    body.put("durationMinutes", 45);
    body.put(
        "classTimes",
        List.of(Map.of("daysOfWeek", List.of("FRIDAY"), "startTime", "18:00:00", "capacity", 10)));

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/marketplace/trainer/classes",
            HttpMethod.POST,
            new HttpEntity<>(body, authHeaders(trainerAccessToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  @DisplayName("[CE-3] 스토어 미등록 트레이너의 클래스 등록 시 404 반환")
  void registerClass_noStore_returns404() throws Exception {
    // given: 스토어 없는 신규 트레이너
    String noStoreEmail = randomEmail();
    signupUser(noStoreEmail, DEFAULT_TEST_PASSWORD, "스토어없는트레이너");
    jdbcTemplate.update("UPDATE users SET role = 'TRAINER' WHERE email = ?", noStoreEmail);
    String noStoreToken = loginUser(noStoreEmail, DEFAULT_TEST_PASSWORD);

    // when
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/marketplace/trainer/classes",
            HttpMethod.POST,
            new HttpEntity<>(validClassBody(), authHeaders(noStoreToken)),
            String.class);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("[CE-4] 슬롯 시간이 겹치는 클래스 등록 시 409 반환")
  void registerClass_conflictingSlots_returns409() {
    Map<String, Object> body = new LinkedHashMap<>(validClassBody());
    // 동일 요일 10:00 + 10:30 두 슬롯 (60분 duration → 충돌)
    body.put(
        "classTimes",
        List.of(
            Map.of("daysOfWeek", List.of("MONDAY"), "startTime", "10:00:00", "capacity", 5),
            Map.of("daysOfWeek", List.of("MONDAY"), "startTime", "10:30:00", "capacity", 5)));

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/marketplace/trainer/classes",
            HttpMethod.POST,
            new HttpEntity<>(body, authHeaders(trainerAccessToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  // ============================================================
  // E2E Tests — 클래스 상세 조회 (GET /classes/{classId})
  // ============================================================

  @Test
  @DisplayName("[CE-5] 등록된 클래스 상세 조회 시 200 및 모든 필드 반환")
  void getClassDetail_afterRegister_returns200WithAllFields() throws Exception {
    // given: 클래스 등록
    Long classId = registerClassAndGetId(trainerAccessToken);

    // when: 공개 상세 조회 (인증 불필요)
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/marketplace/classes/" + classId,
            HttpMethod.GET,
            new HttpEntity<>(new HttpHeaders()),
            String.class);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data/classId").asLong()).isEqualTo(classId);
    assertThat(root.at("/data/title").asText()).isEqualTo("PT 60분 기초체력");
    assertThat(root.at("/data/category").asText()).isEqualTo("PT");
    assertThat(root.at("/data/priceAmount").asInt()).isEqualTo(50000);
    assertThat(root.at("/data/durationMinutes").asInt()).isEqualTo(60);
    // 스토어 정보 포함
    assertThat(root.at("/data/store/storeName").asText()).isEqualTo("PT Studio 클래스E2E");
    // 활성 슬롯 포함
    assertThat(root.at("/data/classTimes").isArray()).isTrue();
    assertThat(root.at("/data/classTimes").size()).isGreaterThan(0);
    // 태그 포함
    assertThat(root.at("/data/tags").isArray()).isTrue();
  }

  @Test
  @DisplayName("[CE-6] 존재하지 않는 classId 조회 시 404 반환")
  void getClassDetail_notFound_returns404() {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/marketplace/classes/99999999",
            HttpMethod.GET,
            new HttpEntity<>(new HttpHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  // ============================================================
  // E2E Tests — 클래스 수정 (PUT /trainer/classes/{classId})
  // ============================================================

  @Test
  @DisplayName("[CE-7] 자신의 클래스 수정 시 200 및 동일 classId 반환, 필드 변경 확인")
  void updateClass_ownClass_returns200WithSameClassId() throws Exception {
    // given: 클래스 등록
    Long classId = registerClassAndGetId(trainerAccessToken);

    // when: 클래스 수정
    Map<String, Object> updateBody = new LinkedHashMap<>();
    updateBody.put("title", "PT 90분 중급 업데이트");
    updateBody.put("category", "PT");
    updateBody.put("description", "중급 체력 향상 PT 클래스 (업데이트)");
    updateBody.put("priceAmount", 80000);
    updateBody.put("durationMinutes", 90);
    updateBody.put(
        "classTimes",
        List.of(
            Map.of(
                "daysOfWeek",
                List.of("TUESDAY", "THURSDAY"),
                "startTime",
                "14:00:00",
                "capacity",
                3)));

    ResponseEntity<String> updateResponse =
        restTemplate.exchange(
            baseUrl() + "/marketplace/trainer/classes/" + classId,
            HttpMethod.PUT,
            new HttpEntity<>(updateBody, authHeaders(trainerAccessToken)),
            String.class);

    // then: 200 + 동일 classId
    assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    Long updatedClassId =
        objectMapper.readTree(updateResponse.getBody()).at("/data/classId").asLong();
    assertThat(updatedClassId).isEqualTo(classId);

    // 수정 후 상세 조회로 변경 확인
    ResponseEntity<String> getResponse =
        restTemplate.exchange(
            baseUrl() + "/marketplace/classes/" + classId,
            HttpMethod.GET,
            new HttpEntity<>(new HttpHeaders()),
            String.class);
    JsonNode data = objectMapper.readTree(getResponse.getBody()).at("/data");
    assertThat(data.at("/title").asText()).isEqualTo("PT 90분 중급 업데이트");
    assertThat(data.at("/priceAmount").asInt()).isEqualTo(80000);
    assertThat(data.at("/durationMinutes").asInt()).isEqualTo(90);
  }

  @Test
  @DisplayName("[CE-8] 다른 트레이너의 클래스를 수정하려고 하면 403 반환")
  void updateClass_otherTrainerClass_returns403() throws Exception {
    // given: 첫 번째 트레이너가 클래스 등록
    Long classId = registerClassAndGetId(trainerAccessToken);

    // given: 두 번째 트레이너 생성
    String otherEmail = randomEmail();
    signupUser(otherEmail, DEFAULT_TEST_PASSWORD, "다른트레이너");
    jdbcTemplate.update("UPDATE users SET role = 'TRAINER' WHERE email = ?", otherEmail);
    String otherToken = loginUser(otherEmail, DEFAULT_TEST_PASSWORD);
    upsertStore(otherToken);

    // when: 두 번째 트레이너가 첫 번째 트레이너의 클래스 수정 시도
    Map<String, Object> updateBody = new LinkedHashMap<>();
    updateBody.put("title", "해킹된 제목");
    updateBody.put("category", "PT");
    updateBody.put("description", "설명");
    updateBody.put("priceAmount", 1000);
    updateBody.put("durationMinutes", 60);
    updateBody.put(
        "classTimes",
        List.of(Map.of("daysOfWeek", List.of("MONDAY"), "startTime", "10:00:00", "capacity", 1)));

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/marketplace/trainer/classes/" + classId,
            HttpMethod.PUT,
            new HttpEntity<>(updateBody, authHeaders(otherToken)),
            String.class);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  // ============================================================
  // E2E Tests — 클래스 상태 토글 (PATCH /trainer/classes/{classId}/status)
  // ============================================================

  @Test
  @DisplayName("[CE-9] 클래스 상태 토글 시 200 및 active 필드 변경 확인")
  void toggleClassStatus_validRequest_returns200WithToggledStatus() throws Exception {
    // given: 클래스 등록 (active=true)
    Long classId = registerClassAndGetId(trainerAccessToken);

    // when: 첫 번째 토글 (active → inactive)
    ResponseEntity<String> toggleResponse =
        restTemplate.exchange(
            baseUrl() + "/marketplace/trainer/classes/" + classId + "/status",
            HttpMethod.PATCH,
            new HttpEntity<>(authHeaders(trainerAccessToken)),
            String.class);

    // then: 200 + active=false
    assertThat(toggleResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(toggleResponse.getBody());
    assertThat(root.at("/data/classId").asLong()).isEqualTo(classId);
    assertThat(root.at("/data/active").asBoolean()).isFalse();
  }

  @Test
  @DisplayName("[CE-10] 존재하지 않는 classId 토글 시 404 반환")
  void toggleClassStatus_notFound_returns404() {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/marketplace/trainer/classes/99999999/status",
            HttpMethod.PATCH,
            new HttpEntity<>(authHeaders(trainerAccessToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("[CE-11] 다른 트레이너 클래스 상태 토글 시 403 반환")
  void toggleClassStatus_otherTrainerClass_returns403() throws Exception {
    // given: 첫 번째 트레이너 클래스 등록
    Long classId = registerClassAndGetId(trainerAccessToken);

    // given: 두 번째 트레이너
    String otherEmail = randomEmail();
    signupUser(otherEmail, DEFAULT_TEST_PASSWORD, "타트레이너");
    jdbcTemplate.update("UPDATE users SET role = 'TRAINER' WHERE email = ?", otherEmail);
    String otherToken = loginUser(otherEmail, DEFAULT_TEST_PASSWORD);

    // when: 두 번째 트레이너가 첫 번째 클래스 토글 시도
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/marketplace/trainer/classes/" + classId + "/status",
            HttpMethod.PATCH,
            new HttpEntity<>(authHeaders(otherToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  // ============================================================
  // E2E Tests — 트레이너 클래스 목록 (GET /trainer/classes)
  // ============================================================

  @Test
  @DisplayName("[CE-12] 등록한 클래스가 트레이너 클래스 목록에 포함된다")
  void getTrainerClasses_afterRegister_includesRegisteredClass() throws Exception {
    // given: 클래스 등록
    Long classId = registerClassAndGetId(trainerAccessToken);

    // when: 트레이너 클래스 목록 조회
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/marketplace/trainer/classes",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(trainerAccessToken)),
            String.class);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    JsonNode classes = root.at("/data/classes");
    assertThat(classes.isArray()).isTrue();
    boolean found = false;
    for (JsonNode c : classes) {
      if (c.at("/classId").asLong() == classId) {
        found = true;
        assertThat(c.at("/title").asText()).isEqualTo("PT 60분 기초체력");
        break;
      }
    }
    assertThat(found).isTrue();
  }

  @Test
  @DisplayName("[CE-13] 클래스가 없는 트레이너의 목록 조회 시 빈 리스트 반환")
  void getTrainerClasses_noClasses_returnsEmptyList() throws Exception {
    // given: 클래스 없는 신규 트레이너
    String freshEmail = randomEmail();
    signupUser(freshEmail, DEFAULT_TEST_PASSWORD, "신규트레이너");
    jdbcTemplate.update("UPDATE users SET role = 'TRAINER' WHERE email = ?", freshEmail);
    String freshToken = loginUser(freshEmail, DEFAULT_TEST_PASSWORD);

    // when
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/marketplace/trainer/classes",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(freshToken)),
            String.class);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode classes = objectMapper.readTree(response.getBody()).at("/data/classes");
    assertThat(classes.size()).isZero();
  }

  // ============================================================
  // E2E Tests — 공개 클래스 목록 (GET /classes)
  // ============================================================

  @Test
  @DisplayName("[CE-14] 클래스 등록 후 공개 목록에서 조회 가능하다 (active만 노출)")
  void getClasses_afterRegister_publicListIncludesActiveClass() throws Exception {
    // given: 클래스 등록 (active=true)
    Long classId = registerClassAndGetId(trainerAccessToken);

    // when: 공개 목록 조회 (인증 불필요)
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/marketplace/classes",
            HttpMethod.GET,
            new HttpEntity<>(new HttpHeaders()),
            String.class);

    // then: 200 + 등록된 클래스 포함
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    JsonNode classes = root.at("/data/classes");
    assertThat(classes.isArray()).isTrue();
    boolean found = false;
    for (JsonNode c : classes) {
      if (c.at("/classId").asLong() == classId) {
        found = true;
        break;
      }
    }
    assertThat(found).isTrue();
  }

  @Test
  @DisplayName("[CE-15] inactive 클래스는 공개 목록에서 제외된다")
  void getClasses_inactiveClass_notIncludedInPublicList() throws Exception {
    // given: 클래스 등록 후 비활성화
    Long classId = registerClassAndGetId(trainerAccessToken);
    restTemplate.exchange(
        baseUrl() + "/marketplace/trainer/classes/" + classId + "/status",
        HttpMethod.PATCH,
        new HttpEntity<>(authHeaders(trainerAccessToken)),
        String.class);

    // when: 공개 목록 조회
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/marketplace/classes",
            HttpMethod.GET,
            new HttpEntity<>(new HttpHeaders()),
            String.class);

    // then: inactive 클래스가 목록에 없어야 함
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode classes = objectMapper.readTree(response.getBody()).at("/data/classes");
    for (JsonNode c : classes) {
      assertThat(c.at("/classId").asLong()).isNotEqualTo(classId);
    }
  }

  // ============================================================
  // E2E Tests — 입력 검증 (400)
  // ============================================================

  @Test
  @DisplayName("[CE-16] title 누락 시 400 반환")
  void registerClass_missingTitle_returns400() {
    Map<String, Object> body = new LinkedHashMap<>(validClassBody());
    body.remove("title");

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/marketplace/trainer/classes",
            HttpMethod.POST,
            new HttpEntity<>(body, authHeaders(trainerAccessToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("[CE-17] priceAmount=0 (비양수) 시 400 반환")
  void registerClass_zeroPriceAmount_returns400() {
    Map<String, Object> body = new LinkedHashMap<>(validClassBody());
    body.put("priceAmount", 0);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/marketplace/trainer/classes",
            HttpMethod.POST,
            new HttpEntity<>(body, authHeaders(trainerAccessToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("[CE-18] classTimes 누락 시 400 반환")
  void registerClass_missingClassTimes_returns400() {
    Map<String, Object> body = new LinkedHashMap<>(validClassBody());
    body.remove("classTimes");

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/marketplace/trainer/classes",
            HttpMethod.POST,
            new HttpEntity<>(body, authHeaders(trainerAccessToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("[CE-19] durationMinutes 초과(1441) 시 400 반환")
  void registerClass_durationExceedsMax_returns400() {
    Map<String, Object> body = new LinkedHashMap<>(validClassBody());
    body.put("durationMinutes", 1441);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/marketplace/trainer/classes",
            HttpMethod.POST,
            new HttpEntity<>(body, authHeaders(trainerAccessToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("[CE-20] 태그 4개 초과 시 400 반환 (최대 3개)")
  void registerClass_tooManyTags_returns400() {
    Map<String, Object> body = new LinkedHashMap<>(validClassBody());
    body.put("tags", List.of("a", "b", "c", "d"));

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/marketplace/trainer/classes",
            HttpMethod.POST,
            new HttpEntity<>(body, authHeaders(trainerAccessToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  // ============================================================
  // E2E Tests — 인증 (401)
  // ============================================================

  @Test
  @DisplayName("[CE-21] 인증 없이 클래스 등록 시 401 반환")
  void registerClass_withoutAuth_returns401() {
    HttpHeaders noAuthHeaders = new HttpHeaders();
    noAuthHeaders.setContentType(MediaType.APPLICATION_JSON);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/marketplace/trainer/classes",
            HttpMethod.POST,
            new HttpEntity<>(validClassBody(), noAuthHeaders),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("[CE-22] 인증 없이 클래스 수정 시 401 반환")
  void updateClass_withoutAuth_returns401() throws Exception {
    Long classId = registerClassAndGetId(trainerAccessToken);

    HttpHeaders noAuthHeaders = new HttpHeaders();
    noAuthHeaders.setContentType(MediaType.APPLICATION_JSON);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/marketplace/trainer/classes/" + classId,
            HttpMethod.PUT,
            new HttpEntity<>(validClassBody(), noAuthHeaders),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("[CE-23] 인증 없이 상태 토글 시 401 반환")
  void toggleClassStatus_withoutAuth_returns401() throws Exception {
    Long classId = registerClassAndGetId(trainerAccessToken);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/marketplace/trainer/classes/" + classId + "/status",
            HttpMethod.PATCH,
            new HttpEntity<>(new HttpHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("[CE-24] 인증 없이 트레이너 클래스 목록 조회 시 401 반환")
  void getTrainerClasses_withoutAuth_returns401() {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/marketplace/trainer/classes",
            HttpMethod.GET,
            new HttpEntity<>(new HttpHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  // ============================================================
  // E2E Tests — 전체 흐름 (Register → Detail → Update → Toggle → List)
  // ============================================================

  @Test
  @DisplayName("[CE-25] 클래스 전체 라이프사이클 — 등록 → 조회 → 수정 → 비활성화 → 공개 목록 제외 확인")
  void classLifecycle_registerThenUpdateThenToggleInactive() throws Exception {
    // Step 1: 클래스 등록
    ResponseEntity<String> registerResp =
        restTemplate.exchange(
            baseUrl() + "/marketplace/trainer/classes",
            HttpMethod.POST,
            new HttpEntity<>(validClassBody(), authHeaders(trainerAccessToken)),
            String.class);
    assertThat(registerResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    Long classId = objectMapper.readTree(registerResp.getBody()).at("/data/classId").asLong();
    assertThat(classId).isPositive();

    // Step 2: 상세 조회 → 등록 값 일치 확인
    ResponseEntity<String> detailResp =
        restTemplate.exchange(
            baseUrl() + "/marketplace/classes/" + classId,
            HttpMethod.GET,
            new HttpEntity<>(new HttpHeaders()),
            String.class);
    assertThat(detailResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode detail = objectMapper.readTree(detailResp.getBody()).at("/data");
    assertThat(detail.at("/title").asText()).isEqualTo("PT 60분 기초체력");

    // Step 3: 클래스 수정
    Map<String, Object> updateBody = new LinkedHashMap<>();
    updateBody.put("title", "PT 90분 중급 [수정완료]");
    updateBody.put("category", "PT");
    updateBody.put("description", "수정된 설명");
    updateBody.put("priceAmount", 75000);
    updateBody.put("durationMinutes", 90);
    updateBody.put(
        "classTimes",
        List.of(Map.of("daysOfWeek", List.of("TUESDAY"), "startTime", "11:00:00", "capacity", 4)));

    ResponseEntity<String> updateResp =
        restTemplate.exchange(
            baseUrl() + "/marketplace/trainer/classes/" + classId,
            HttpMethod.PUT,
            new HttpEntity<>(updateBody, authHeaders(trainerAccessToken)),
            String.class);
    assertThat(updateResp.getStatusCode()).isEqualTo(HttpStatus.OK);

    // Step 4: 비활성화 (active → inactive)
    ResponseEntity<String> toggleResp =
        restTemplate.exchange(
            baseUrl() + "/marketplace/trainer/classes/" + classId + "/status",
            HttpMethod.PATCH,
            new HttpEntity<>(authHeaders(trainerAccessToken)),
            String.class);
    assertThat(toggleResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(objectMapper.readTree(toggleResp.getBody()).at("/data/active").asBoolean())
        .isFalse();

    // Step 5: 공개 목록에서 제외 확인
    ResponseEntity<String> listResp =
        restTemplate.exchange(
            baseUrl() + "/marketplace/classes",
            HttpMethod.GET,
            new HttpEntity<>(new HttpHeaders()),
            String.class);
    JsonNode classes = objectMapper.readTree(listResp.getBody()).at("/data/classes");
    for (JsonNode c : classes) {
      assertThat(c.at("/classId").asLong()).isNotEqualTo(classId);
    }
  }
}
