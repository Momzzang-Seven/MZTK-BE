package momzzangseven.mztkbe.integration.e2e.image;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.image.infrastructure.persistence.entity.ImageEntity;
import momzzangseven.mztkbe.modules.image.infrastructure.persistence.repository.ImageJpaRepository;
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
 * GetImagesByIds E2E test (Local Server + Real PostgreSQL).
 *
 * <p>Scenarios [E-1 ~ E-17]:
 *
 * <ul>
 *   <li>[E-1] Unauthenticated request → 401
 *   <li>[E-2] Missing ids param → 400 VALIDATION_002
 *   <li>[E-3] Missing referenceType param → 400 VALIDATION_002
 *   <li>[E-4] Missing referenceId param → 400 VALIDATION_002
 *   <li>[E-5] Unknown referenceType enum → 400 VALIDATION_001
 *   <li>[E-6] Internal-only referenceType (MARKET_STORE_THUMB) → 400 IMAGE_006
 *   <li>[E-7] Single COMPLETED image → 200 + response shape verification
 *   <li>[E-8] Multiple images (COMPLETED + PENDING) → 200 + finalObjectKey null check
 *   <li>[E-9] Non-existent id soft-miss → 200 + images=[]
 *   <li>[E-10] Image owned by another user → 403 IMAGE_009
 *   <li>[E-11] Mismatched referenceId → 403 IMAGE_009
 *   <li>[E-12] ids count exceeds limit (11) → 400 IMAGE_004
 *   <li>[E-13] MARKET_STORE virtual type w/ concrete subtype images → 200
 *   <li>[E-14] Read-only transaction — updated_at not modified
 *   <li>[E-15] imgOrder stored out-of-order — response sorted ascending
 *   <li>[E-16] MARKET_STORE: response referenceType is MARKET_STORE (no THUMB/DETAIL leak)
 *   <li>[E-17] MARKET_CLASS: response referenceType is MARKET_CLASS (no THUMB/DETAIL leak)
 * </ul>
 */
@DisplayName("[E2E] GetImagesByIds 전체 흐름 테스트 (Local Server + Real PostgreSQL)")
class GetImagesByIdsE2ETest extends E2ETestBase {

  @Autowired private ImageJpaRepository imageJpaRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  private String baseUrl;
  private String accessToken;
  private Long currentUserId;

  // ============================================================
  // Helper Methods
  // ============================================================

  private static String uniqueEmail() {
    return "e2e-gbi-"
        + UUID.randomUUID().toString().replace("-", "").substring(0, 10)
        + "@test.com";
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

  private Long getUserIdByEmail(String email) {
    return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
  }

  private Long saveImage(
      Long userId,
      String referenceType,
      Long referenceId,
      String status,
      String finalObjectKey,
      int imgOrder) {
    String tmpKey = "e2e/gbi/tmp/" + UUID.randomUUID() + ".jpg";
    ImageEntity image =
        ImageEntity.builder()
            .userId(userId)
            .referenceType(referenceType)
            .referenceId(referenceId)
            .status(status)
            .tmpObjectKey(tmpKey)
            .finalObjectKey(finalObjectKey)
            .imgOrder(imgOrder)
            .build();
    return imageJpaRepository.save(image).getId();
  }

  private ResponseEntity<String> getImages(
      String authToken, List<Long> ids, String referenceType, Long referenceId) {
    HttpHeaders headers = new HttpHeaders();
    if (authToken != null) {
      headers.setBearerAuth(authToken);
    }

    StringBuilder url = new StringBuilder(baseUrl + "/images?");
    for (Long id : ids) {
      url.append("ids=").append(id).append("&");
    }
    url.append("referenceType=").append(referenceType).append("&referenceId=").append(referenceId);

    return restTemplate.exchange(
        url.toString(), HttpMethod.GET, new HttpEntity<>(headers), String.class);
  }

  // ============================================================
  // Setup
  // ============================================================

  @BeforeEach
  void setUp() throws Exception {
    baseUrl = "http://localhost:" + port;
    String email = uniqueEmail();
    signup(email, "Test@1234!", "이미지조회E2E유저");
    accessToken = loginAndGetAccessToken(email, "Test@1234!");
    currentUserId = getUserIdByEmail(email);
  }

  // ============================================================
  // E2E Tests
  // ============================================================

  @Test
  @DisplayName("[E-1] 미인증 요청 시 401 반환")
  void getImages_returns401_whenUnauthenticated() {
    ResponseEntity<String> response = getImages(null, List.of(1L), "COMMUNITY_FREE", 100L);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("[E-2] ids 파라미터 누락 시 400 VALIDATION_002 반환")
  void getImages_returns400_whenIdsMissing() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/images?referenceType=COMMUNITY_FREE&referenceId=100",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.at("/code").asText()).isEqualTo("VALIDATION_002");
  }

  @Test
  @DisplayName("[E-3] referenceType 파라미터 누락 시 400 VALIDATION_002 반환")
  void getImages_returns400_whenReferenceTypeMissing() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/images?ids=1&referenceId=100",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.at("/code").asText()).isEqualTo("VALIDATION_002");
  }

  @Test
  @DisplayName("[E-4] referenceId 파라미터 누락 시 400 VALIDATION_002 반환")
  void getImages_returns400_whenReferenceIdMissing() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/images?ids=1&referenceType=COMMUNITY_FREE",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.at("/code").asText()).isEqualTo("VALIDATION_002");
  }

  @Test
  @DisplayName("[E-5] 알 수 없는 referenceType enum 값 → 400 VALIDATION_001")
  void getImages_returns400_whenReferenceTypeIsInvalidEnum() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/images?ids=1&referenceType=INVALID_TYPE&referenceId=100",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.at("/code").asText()).isEqualTo("VALIDATION_001");
  }

  @Test
  @DisplayName("[E-6] internal-only referenceType(MARKET_STORE_THUMB) → 400 IMAGE_006")
  void getImages_returns400_whenInternalOnlyReferenceType() throws Exception {
    ResponseEntity<String> response =
        getImages(accessToken, List.of(1L), "MARKET_STORE_THUMB", 100L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.at("/code").asText()).isEqualTo("IMAGE_006");
  }

  @Test
  @DisplayName("[E-7] 단일 COMPLETED 이미지 정상 조회 — 200 + 응답 구조 전체 검증")
  void getImages_returns200_withSingleCompletedImage() throws Exception {
    // given
    Long imageId =
        saveImage(
            currentUserId,
            "COMMUNITY_FREE",
            100L,
            "COMPLETED",
            "public/community/free/test.webp",
            1);

    // when
    ResponseEntity<String> response =
        getImages(accessToken, List.of(imageId), "COMMUNITY_FREE", 100L);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.at("/status").asText()).isEqualTo("SUCCESS");

    JsonNode images = body.at("/data/images");
    assertThat(images.size()).isEqualTo(1);

    JsonNode img = images.get(0);
    assertThat(img.at("/imageId").asLong()).isEqualTo(imageId);
    assertThat(img.at("/userId").asLong()).isEqualTo(currentUserId);
    assertThat(img.at("/referenceType").asText()).isEqualTo("COMMUNITY_FREE");
    assertThat(img.at("/referenceId").asLong()).isEqualTo(100L);
    assertThat(img.at("/status").asText()).isEqualTo("COMPLETED");
    assertThat(img.at("/finalObjectKey").asText()).isEqualTo("public/community/free/test.webp");
    assertThat(img.at("/imgOrder").asInt()).isEqualTo(1);
    assertThat(img.at("/createdAt").asText()).isNotBlank();
    assertThat(img.at("/updatedAt").asText()).isNotBlank();
  }

  @Test
  @DisplayName("[E-8] 복수 이미지(COMPLETED + PENDING) — 200 + finalObjectKey null 여부 검증")
  void getImages_returns200_withMultipleImagesIncludingPending() throws Exception {
    // given
    Long completedId =
        saveImage(currentUserId, "COMMUNITY_FREE", 100L, "COMPLETED", "public/free/1.webp", 1);
    Long pendingId = saveImage(currentUserId, "COMMUNITY_FREE", 100L, "PENDING", null, 2);

    // when
    ResponseEntity<String> response =
        getImages(accessToken, List.of(completedId, pendingId), "COMMUNITY_FREE", 100L);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode images = objectMapper.readTree(response.getBody()).at("/data/images");
    assertThat(images.size()).isEqualTo(2);

    JsonNode completedImg =
        images.get(0).at("/imageId").asLong() == completedId ? images.get(0) : images.get(1);
    assertThat(completedImg.at("/status").asText()).isEqualTo("COMPLETED");
    assertThat(completedImg.at("/finalObjectKey").asText()).isNotBlank();

    JsonNode pendingImg =
        images.get(0).at("/imageId").asLong() == pendingId ? images.get(0) : images.get(1);
    assertThat(pendingImg.at("/status").asText()).isEqualTo("PENDING");
    assertThat(pendingImg.at("/finalObjectKey").isNull()).isTrue();
  }

  @Test
  @DisplayName("[E-9] 존재하지 않는 ID soft-miss — 200 + images=[] 빈 배열")
  void getImages_returns200_withEmptyArrayWhenIdNotFound() throws Exception {
    ResponseEntity<String> response =
        getImages(accessToken, List.of(99999999L), "COMMUNITY_FREE", 100L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode images = objectMapper.readTree(response.getBody()).at("/data/images");
    assertThat(images.size()).isEqualTo(0);
  }

  @Test
  @DisplayName("[E-10] 다른 사용자의 이미지 조회 시도 → 403 IMAGE_009")
  void getImages_returns403_whenImageBelongsToAnotherUser() throws Exception {
    // given: another user owns the image
    String otherEmail = uniqueEmail();
    signup(otherEmail, "Test@1234!", "다른유저");
    Long otherUserId = getUserIdByEmail(otherEmail);

    Long imageId = saveImage(otherUserId, "COMMUNITY_FREE", 100L, "COMPLETED", "key.webp", 1);

    // when
    ResponseEntity<String> response =
        getImages(accessToken, List.of(imageId), "COMMUNITY_FREE", 100L);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.at("/code").asText()).isEqualTo("IMAGE_009");
  }

  @Test
  @DisplayName("[E-11] 이미지의 referenceId와 요청 referenceId 불일치 → 403 IMAGE_009")
  void getImages_returns403_whenReferenceIdMismatch() throws Exception {
    Long imageId = saveImage(currentUserId, "COMMUNITY_FREE", 100L, "COMPLETED", "key.webp", 1);

    ResponseEntity<String> response =
        getImages(accessToken, List.of(imageId), "COMMUNITY_FREE", 999L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.at("/code").asText()).isEqualTo("IMAGE_009");
  }

  @Test
  @DisplayName("[E-12] ids 개수 11개(COMMUNITY_FREE 최대 10) 초과 → 400 IMAGE_004")
  void getImages_returns400_whenIdsCountExceedsLimit() throws Exception {
    List<Long> elevenIds = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L);
    ResponseEntity<String> response = getImages(accessToken, elevenIds, "COMMUNITY_FREE", 100L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.at("/code").asText()).isEqualTo("IMAGE_004");
  }

  @Test
  @DisplayName("[E-13] MARKET_STORE virtual type — concrete subtype 이미지 정상 조회 → 200")
  void getImages_returns200_withMarketStoreVirtualType() throws Exception {
    Long thumbId =
        saveImage(currentUserId, "MARKET_STORE_THUMB", 200L, "COMPLETED", "store/thumb.webp", 1);
    Long detailId =
        saveImage(currentUserId, "MARKET_STORE_DETAIL", 200L, "COMPLETED", "store/detail.webp", 2);

    ResponseEntity<String> response =
        getImages(accessToken, List.of(thumbId, detailId), "MARKET_STORE", 200L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(body.at("/data/images").size()).isEqualTo(2);
  }

  @Test
  @DisplayName("[E-15] imgOrder 역순 저장 — 응답은 imgOrder 오름차순 정렬 보장")
  void getImages_returns200_sortedByImgOrderAscending() throws Exception {
    Long id3 = saveImage(currentUserId, "COMMUNITY_FREE", 100L, "COMPLETED", "public/3.webp", 3);
    Long id1 = saveImage(currentUserId, "COMMUNITY_FREE", 100L, "COMPLETED", "public/1.webp", 1);
    Long id2 = saveImage(currentUserId, "COMMUNITY_FREE", 100L, "COMPLETED", "public/2.webp", 2);

    ResponseEntity<String> response =
        getImages(accessToken, List.of(id3, id1, id2), "COMMUNITY_FREE", 100L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode images = objectMapper.readTree(response.getBody()).at("/data/images");
    assertThat(images.size()).isEqualTo(3);
    assertThat(images.get(0).at("/imgOrder").asInt()).isEqualTo(1);
    assertThat(images.get(1).at("/imgOrder").asInt()).isEqualTo(2);
    assertThat(images.get(2).at("/imgOrder").asInt()).isEqualTo(3);
  }

  @Test
  @DisplayName(
      "[E-16] MARKET_STORE virtual type — 응답의 referenceType은 MARKET_STORE 반환 (내부 THUMB/DETAIL 노출 없음)")
  void getImages_returns200_withMarketStoreConvertedToRequestFacing() throws Exception {
    Long thumbId =
        saveImage(currentUserId, "MARKET_STORE_THUMB", 200L, "COMPLETED", "store/thumb.webp", 1);
    Long detailId =
        saveImage(currentUserId, "MARKET_STORE_DETAIL", 200L, "COMPLETED", "store/detail.webp", 2);

    ResponseEntity<String> response =
        getImages(accessToken, List.of(thumbId, detailId), "MARKET_STORE", 200L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode images = objectMapper.readTree(response.getBody()).at("/data/images");
    assertThat(images.size()).isEqualTo(2);
    for (int i = 0; i < images.size(); i++) {
      assertThat(images.get(i).at("/referenceType").asText()).isEqualTo("MARKET_STORE");
    }
  }

  @Test
  @DisplayName(
      "[E-17] MARKET_CLASS virtual type — 응답의 referenceType은 MARKET_CLASS 반환 (내부 THUMB/DETAIL 노출 없음)")
  void getImages_returns200_withMarketClassConvertedToRequestFacing() throws Exception {
    Long thumbId =
        saveImage(currentUserId, "MARKET_CLASS_THUMB", 300L, "COMPLETED", "class/thumb.webp", 1);
    Long detailId =
        saveImage(currentUserId, "MARKET_CLASS_DETAIL", 300L, "COMPLETED", "class/detail.webp", 2);

    ResponseEntity<String> response =
        getImages(accessToken, List.of(thumbId, detailId), "MARKET_CLASS", 300L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode images = objectMapper.readTree(response.getBody()).at("/data/images");
    assertThat(images.size()).isEqualTo(2);
    for (int i = 0; i < images.size(); i++) {
      assertThat(images.get(i).at("/referenceType").asText()).isEqualTo("MARKET_CLASS");
    }
  }

  @Test
  @DisplayName("[E-14] 읽기 전용 트랜잭션 — 이미지 조회 후 updated_at 변경 없음")
  void getImages_readOnlyTransaction_doesNotModifyUpdatedAt() throws Exception {
    Long imageId =
        saveImage(currentUserId, "COMMUNITY_FREE", 100L, "COMPLETED", "public/free/test.webp", 1);

    Instant updatedAtBefore =
        jdbcTemplate.queryForObject(
            "SELECT updated_at FROM images WHERE id = ?", Instant.class, imageId);

    ResponseEntity<String> response =
        getImages(accessToken, List.of(imageId), "COMMUNITY_FREE", 100L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    Instant updatedAtAfter =
        jdbcTemplate.queryForObject(
            "SELECT updated_at FROM images WHERE id = ?", Instant.class, imageId);

    assertThat(updatedAtAfter).isEqualTo(updatedAtBefore);
  }
}
