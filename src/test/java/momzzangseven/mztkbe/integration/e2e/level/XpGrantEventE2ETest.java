package momzzangseven.mztkbe.integration.e2e.level;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Verifies the MOM-465 contract end-to-end: create write paths publish a domain event inside their
 * own transaction and the {@code level} module grants XP on AFTER_COMMIT, so the XP ledger row is
 * durably written (by the time the synchronous response returns) without the producer ever holding
 * a second DB connection.
 *
 * <p>The byte-identical idempotency keys ({@code post:create:*}, {@code comment:create:*}, {@code
 * workout:location-verify:*}) are asserted directly against {@code xp_ledger}.
 */
@DisplayName("[E2E] MOM-465 XP 적립 이벤트 이관 검증")
class XpGrantEventE2ETest extends E2ETestBase {

  private static final double TEST_LATITUDE = 37.5665;
  private static final double TEST_LONGITUDE = 126.978;

  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private GeocodingPort geocodingPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  private TestUser user;

  @BeforeEach
  void setUp() {
    given(geocodingPort.geocode(anyString()))
        .willReturn(CoordinatesInfo.of(TEST_LATITUDE, TEST_LONGITUDE));
    given(geocodingPort.reverseGeocode(anyDouble(), anyDouble()))
        .willReturn(AddressInfo.of("서울특별시 중구 세종대로 110", "04524"));
    user = signupAndLogin("xp-event-e2e-user");
  }

  @Test
  @DisplayName("자유 게시글 생성 → PostCreatedEvent → AFTER_COMMIT 으로 POST XP 적립")
  void freePostCreation_grantsPostXpViaEvent() throws Exception {
    Long postId = createFreePost("xp event free post");

    assertThat(countXpLedger(user.userId(), "POST", "post:create:" + postId)).isEqualTo(1);
  }

  @Test
  @DisplayName("댓글 생성 → CommentCreatedEvent → AFTER_COMMIT 으로 COMMENT XP 적립")
  void commentCreation_grantsCommentXpViaEvent() throws Exception {
    Long postId = createFreePost("xp event post for comment");

    Long commentId = createComment(postId, "xp event comment");

    assertThat(countXpLedger(user.userId(), "COMMENT", "comment:create:" + commentId)).isEqualTo(1);
  }

  @Test
  @DisplayName("위치 인증 성공 → LocationVerifiedEvent → AFTER_COMMIT 으로 WORKOUT XP 적립")
  void locationVerification_grantsWorkoutXpViaEvent() throws Exception {
    Long locationId = registerLocation();

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/locations/verify",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of(
                    "locationId", locationId,
                    "currentLatitude", TEST_LATITUDE,
                    "currentLongitude", TEST_LONGITUDE),
                bearerJsonHeaders(user.accessToken())),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/data/isVerified").asBoolean()).isTrue();
    // Async grant: the response reports verification success with the standard reward value.
    assertThat(root.at("/data/xpGranted").asBoolean()).isTrue();
    assertThat(root.at("/data/grantedXp").asInt()).isEqualTo(100);

    String today = LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.BASIC_ISO_DATE);
    String workoutKey = "workout:location-verify:" + user.userId() + ":" + locationId + ":" + today;
    assertThat(countXpLedger(user.userId(), "WORKOUT", workoutKey)).isEqualTo(1);
  }

  // ============================================================
  // Helpers
  // ============================================================

  private Long createFreePost(String content) throws Exception {
    ResponseEntity<String> res =
        restTemplate.exchange(
            baseUrl() + "/posts/free",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("content", content, "imageIds", List.of()),
                bearerJsonHeaders(user.accessToken())),
            String.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return objectMapper.readTree(res.getBody()).at("/data/postId").asLong();
  }

  private Long createComment(Long postId, String content) throws Exception {
    ResponseEntity<String> res =
        restTemplate.exchange(
            baseUrl() + "/posts/" + postId + "/comments",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("content", content), bearerJsonHeaders(user.accessToken())),
            String.class);
    assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    return objectMapper.readTree(res.getBody()).at("/data/commentId").asLong();
  }

  private Long registerLocation() throws Exception {
    ResponseEntity<String> res =
        restTemplate.exchange(
            baseUrl() + "/users/me/locations/register",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of(
                    "locationName", "xp event 인증 위치",
                    "latitude", TEST_LATITUDE,
                    "longitude", TEST_LONGITUDE),
                bearerJsonHeaders(user.accessToken())),
            String.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    return objectMapper.readTree(res.getBody()).at("/data/locationId").asLong();
  }

  private int countXpLedger(Long userId, String type, String idempotencyKey) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM xp_ledger WHERE user_id = ? AND type = ? AND idempotency_key = ?",
            Integer.class,
            userId,
            type,
            idempotencyKey);
    return count == null ? 0 : count;
  }
}
