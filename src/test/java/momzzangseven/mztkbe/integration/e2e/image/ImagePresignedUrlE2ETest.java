package momzzangseven.mztkbe.integration.e2e.image;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import momzzangseven.mztkbe.modules.auth.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.auth.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.image.infrastructure.persistence.entity.ImageEntity;
import momzzangseven.mztkbe.modules.image.infrastructure.persistence.repository.ImageJpaRepository;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Presigned URL 발급 E2E 테스트 (Local Server + Real PostgreSQL).
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
 *   <li>[H-1] COMMUNITY_FREE 단일 이미지 → DB 필드 전체 검증 (status, imgOrder, referenceType, referenceId,
 *       tmpObjectKey prefix)
 *   <li>[H-5] WORKOUT 단일 이미지 → tmp/ 미포함 경로를 DB 저장값으로 확인
 *   <li>[H-6] MARKET 단일 이미지 → n+1 확장: DB rows 2개 (MARKET_THUMB + MARKET_DETAIL)
 *   <li>[H-7] MARKET 3장 → DB rows 4개, imgOrder 1~4 연속, 경로/확장자 분기 검증
 *   <li>[E-13] 혼합 리스트 → 트랜잭션 롤백으로 부분 저장 없음
 * </ul>
 */
@Tag("e2e")
@ActiveProfiles("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("[E2E] 이미지 Presigned URL 발급 전체 흐름 (Local Server + Real PostgreSQL)")
class ImagePresignedUrlE2ETest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ImageJpaRepository imageJpaRepository;

  @MockBean private KakaoAuthPort kakaoAuthPort;
  @MockBean private GoogleAuthPort googleAuthPort;
  @MockBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  private String baseUrl;
  private String accessToken;

  /** 테스트 중 생성된 tmpObjectKey 추적 — @AfterEach 에서 DB 정리에 사용. */
  private final List<String> createdKeys = new ArrayList<>();

  // ============================================================
  // Helper Methods
  // ============================================================

  private static String uniqueEmail() {
    return "e2e-img-"
        + UUID.randomUUID().toString().replace("-", "").substring(0, 10)
        + "@test.com";
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
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    return objectMapper.readTree(response.getBody()).at("/data/accessToken").asText();
  }

  private ResponseEntity<String> issuePresignedUrls(String referenceType, List<String> images) {
    Map<String, Object> body = Map.of("referenceType", referenceType, "images", images);
    return restTemplate.exchange(
        baseUrl + "/images/presigned-urls",
        HttpMethod.POST,
        new HttpEntity<>(body, authHeaders()),
        String.class);
  }

  private ImageEntity findOrFail(String tmpObjectKey) {
    return imageJpaRepository
        .findByTmpObjectKey(tmpObjectKey)
        .orElseThrow(
            () ->
                new AssertionError(
                    "Expected DB row for tmpObjectKey=" + tmpObjectKey + " but not found"));
  }

  // ============================================================
  // Setup / Teardown
  // ============================================================

  @BeforeEach
  void setUp() throws Exception {
    baseUrl = "http://localhost:" + port;
    String email = uniqueEmail();
    signup(email, "Test@1234!", "이미지E2E유저");
    accessToken = loginAndGetAccessToken(email, "Test@1234!");
  }

  @AfterEach
  void cleanup() {
    createdKeys.forEach(
        key -> imageJpaRepository.findByTmpObjectKey(key).ifPresent(imageJpaRepository::delete));
    createdKeys.clear();
  }

  // ============================================================
  // E2E Tests
  // ============================================================

  @Test
  @DisplayName("[H-1] COMMUNITY_FREE 단일 이미지 → 200 + DB 필드 전체 검증")
  void h1_communityFree_singleImage_verifyAllDbFields() throws Exception {
    // when
    ResponseEntity<String> response = issuePresignedUrls("COMMUNITY_FREE", List.of("photo.jpg"));

    // then — HTTP 응답
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode items = objectMapper.readTree(response.getBody()).at("/data/items");
    assertThat(items.size()).isEqualTo(1);

    String tmpObjectKey = items.get(0).at("/tmpObjectKey").asText();
    String presignedUrl = items.get(0).at("/presignedUrl").asText();
    createdKeys.add(tmpObjectKey);

    // 응답: tmpObjectKey prefix, 확장자, presignedUrl 비어있지 않음
    assertThat(tmpObjectKey).startsWith("public/community/free/tmp/");
    assertThat(tmpObjectKey).endsWith(".jpg");
    assertThat(presignedUrl).isNotBlank();

    // then — DB 검증
    ImageEntity saved = findOrFail(tmpObjectKey);
    assertThat(saved.getReferenceType()).isEqualTo("COMMUNITY_FREE");
    assertThat(saved.getStatus()).isEqualTo("PENDING");
    assertThat(saved.getReferenceId()).isNull();
    assertThat(saved.getImgOrder()).isEqualTo(1);
    assertThat(saved.getTmpObjectKey()).isEqualTo(tmpObjectKey);
    assertThat(saved.getFinalObjectKey()).isNull();
    assertThat(saved.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("[H-5] WORKOUT 단일 이미지 → private/workout/ 경로 (tmp/ 없음) + DB 검증")
  void h5_workout_singleImage_noTmpSubfolder_verifyDbFields() throws Exception {
    // when
    ResponseEntity<String> response = issuePresignedUrls("WORKOUT", List.of("exercise.jpg"));

    // then — HTTP 응답
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode items = objectMapper.readTree(response.getBody()).at("/data/items");
    assertThat(items.size()).isEqualTo(1);

    String tmpObjectKey = items.get(0).at("/tmpObjectKey").asText();
    createdKeys.add(tmpObjectKey);

    // WORKOUT: private/workout/{uuid}.jpg — tmp/ 서브폴더 없음
    assertThat(tmpObjectKey).startsWith("private/workout/");
    assertThat(tmpObjectKey).endsWith(".jpg");
    assertThat(tmpObjectKey).doesNotContain("/tmp/");

    // then — DB 검증
    ImageEntity saved = findOrFail(tmpObjectKey);
    assertThat(saved.getReferenceType()).isEqualTo("WORKOUT");
    assertThat(saved.getStatus()).isEqualTo("PENDING");
    assertThat(saved.getReferenceId()).isNull();
    assertThat(saved.getImgOrder()).isEqualTo(1);
  }

  @Test
  @DisplayName("[H-6] MARKET 단일 이미지 → items 2개, DB rows 2개 (MARKET_THUMB + MARKET_DETAIL), UUID 상이")
  void h6_market_singleImage_expandedToTwoDbRows() throws Exception {
    // when
    ResponseEntity<String> response = issuePresignedUrls("MARKET", List.of("product.jpg"));

    // then — HTTP 응답: items 2개
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode items = objectMapper.readTree(response.getBody()).at("/data/items");
    assertThat(items.size()).isEqualTo(2);

    String thumbKey = items.get(0).at("/tmpObjectKey").asText();
    String detailKey = items.get(1).at("/tmpObjectKey").asText();
    createdKeys.addAll(List.of(thumbKey, detailKey));

    // 응답: prefix 분기 + 확장자 유지 + UUID가 서로 다름
    assertThat(thumbKey).startsWith("public/market/thumb/tmp/");
    assertThat(thumbKey).endsWith(".jpg");
    assertThat(detailKey).startsWith("public/market/detail/tmp/");
    assertThat(detailKey).endsWith(".jpg");
    assertThat(thumbKey).isNotEqualTo(detailKey);

    // then — DB 검증: reference_type, imgOrder
    ImageEntity thumbRow = findOrFail(thumbKey);
    ImageEntity detailRow = findOrFail(detailKey);

    assertThat(thumbRow.getReferenceType()).isEqualTo("MARKET_THUMB");
    assertThat(thumbRow.getStatus()).isEqualTo("PENDING");
    assertThat(thumbRow.getImgOrder()).isEqualTo(1);
    assertThat(thumbRow.getReferenceId()).isNull();

    assertThat(detailRow.getReferenceType()).isEqualTo("MARKET_DETAIL");
    assertThat(detailRow.getStatus()).isEqualTo("PENDING");
    assertThat(detailRow.getImgOrder()).isEqualTo(2);
    assertThat(detailRow.getReferenceId()).isNull();
  }

  @Test
  @DisplayName("[H-7] MARKET 3장 → items 4개(n+1), DB rows 4개, imgOrder 1~4 연속, 확장자·경로 분기")
  void h7_market_threeImages_fourDbRows_withCorrectOrderAndPaths() throws Exception {
    // when
    ResponseEntity<String> response =
        issuePresignedUrls("MARKET", List.of("main.jpg", "d1.png", "d2.heic"));

    // then — HTTP 응답: items 4개 (3+1)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode items = objectMapper.readTree(response.getBody()).at("/data/items");
    assertThat(items.size()).isEqualTo(4);

    String key0 = items.get(0).at("/tmpObjectKey").asText(); // MARKET_THUMB, main.jpg
    String key1 = items.get(1).at("/tmpObjectKey").asText(); // MARKET_DETAIL, main.jpg
    String key2 = items.get(2).at("/tmpObjectKey").asText(); // MARKET_DETAIL, d1.png
    String key3 = items.get(3).at("/tmpObjectKey").asText(); // MARKET_DETAIL, d2.heic
    createdKeys.addAll(List.of(key0, key1, key2, key3));

    // 응답: prefix + 확장자 분기
    assertThat(key0).startsWith("public/market/thumb/tmp/").endsWith(".jpg");
    assertThat(key1).startsWith("public/market/detail/tmp/").endsWith(".jpg");
    assertThat(key2).startsWith("public/market/detail/tmp/").endsWith(".png");
    assertThat(key3).startsWith("public/market/detail/tmp/").endsWith(".heic");

    // then — DB 검증: reference_type + imgOrder 1~4 연속, MARKET row 없음
    ImageEntity row0 = findOrFail(key0);
    ImageEntity row1 = findOrFail(key1);
    ImageEntity row2 = findOrFail(key2);
    ImageEntity row3 = findOrFail(key3);

    assertThat(row0.getReferenceType()).isEqualTo("MARKET_THUMB");
    assertThat(row1.getReferenceType()).isEqualTo("MARKET_DETAIL");
    assertThat(row2.getReferenceType()).isEqualTo("MARKET_DETAIL");
    assertThat(row3.getReferenceType()).isEqualTo("MARKET_DETAIL");

    assertThat(row0.getImgOrder()).isEqualTo(1);
    assertThat(row1.getImgOrder()).isEqualTo(2);
    assertThat(row2.getImgOrder()).isEqualTo(3);
    assertThat(row3.getImgOrder()).isEqualTo(4);

    // MARKET(virtual type) reference_type은 DB에 저장되지 않아야 함
    List<ImageEntity> allFour = List.of(row0, row1, row2, row3);
    allFour.forEach(
        row -> {
          assertThat(row.getReferenceType()).isNotEqualTo("MARKET");
          assertThat(row.getStatus()).isEqualTo("PENDING");
          assertThat(row.getReferenceId()).isNull();
        });
  }

  @Test
  @DisplayName("[E-13] 혼합 리스트(valid.jpg + invalid.webp) → 400 + DB 부분 저장 없음 (트랜잭션 롤백)")
  void e13_mixedList_returns400_noPartialDbSave() throws Exception {
    // given: 테스트 전 이미지 전체 행 수 기록
    long countBefore = imageJpaRepository.count();

    // when: 유효 + 무효 확장자 혼합 요청
    ResponseEntity<String> response =
        issuePresignedUrls("COMMUNITY_FREE", List.of("valid.jpg", "invalid.webp"));

    // then — 400 반환
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.at("/code").asText()).isEqualTo("IMAGE_005");

    // then — DB: 행 수 변화 없음 (partial save 발생하지 않음)
    long countAfter = imageJpaRepository.count();
    assertThat(countAfter).isEqualTo(countBefore);
  }
}
