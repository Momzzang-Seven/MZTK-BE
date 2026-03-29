package momzzangseven.mztkbe.integration.e2e.comment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import momzzangseven.mztkbe.modules.auth.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.auth.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Comment CRUD E2E test (Local Server + Real PostgreSQL).
 *
 * <p>Execution prerequisites:
 *
 * <ul>
 *   <li>Local PostgreSQL server must be running (see application-integration.yml)
 *   <li>Run with ./gradlew e2eTest
 * </ul>
 *
 * <p>Scenario coverage:
 *
 * <ul>
 *   <li>free post creation -> comment creation/read/update/delete
 *   <li>reply creation and retrieval
 *   <li>unauthenticated request handling
 *   <li>comment XP ledger persistence on successful creation
 * </ul>
 *
 * <p>External APIs (Kakao, Google) are replaced with mocks.
 */
@Tag("e2e")
@ActiveProfiles("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("[E2E] Comment CRUD full flow")
class CommentE2ETest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  private String baseUrl;
  private String accessToken;

  private Long postId;
  private Long commentId;

  private final List<Long> imageIds = new ArrayList<>();

  private static String uniqueEmail() {
    return "e2e-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10) + "@example.com";
  }

  private HttpHeaders authHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(accessToken);
    return headers;
  }

  private ResponseEntity<String> signup(String email, String password, String nickname) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    Map<String, String> body = Map.of("email", email, "password", password, "nickname", nickname);
    return restTemplate.exchange(
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
    JsonNode root = objectMapper.readTree(response.getBody());
    return root.at("/data/accessToken").asText();
  }

  private Long createFreePost(String content) throws Exception {
    Map<String, Object> body = Map.of("content", content, "imageIds", imageIds);
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/posts/free",
            HttpMethod.POST,
            new HttpEntity<>(body, authHeaders()),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    JsonNode root = objectMapper.readTree(response.getBody());
    return root.at("/data/postId").asLong();
  }

  private Long createComment(Long targetPostId, String content, Long parentId) throws Exception {
    Map<String, Object> body =
        parentId != null
            ? Map.of("content", content, "parentId", parentId)
            : Map.of("content", content);
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/posts/" + targetPostId + "/comments",
            HttpMethod.POST,
            new HttpEntity<>(body, authHeaders()),
            String.class);
    assertThat(response.getStatusCode().is2xxSuccessful())
        .as("comment creation should return 2xx: " + response.getBody())
        .isTrue();
    JsonNode root = objectMapper.readTree(response.getBody());
    return root.at("/data/commentId").asLong();
  }

  private Long currentUserId() {
    return jdbcTemplate.queryForObject(
        "SELECT user_id FROM posts WHERE id = ?", Long.class, postId);
  }

  private Integer countCommentXpLedger(Long userId, Long targetCommentId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM xp_ledger WHERE user_id = ? AND type = 'COMMENT' AND idempotency_key = ?",
        Integer.class,
        userId,
        "comment:create:" + targetCommentId);
  }

  private Integer loadCommentXpAmount(Long userId, Long targetCommentId) {
    return jdbcTemplate.queryForObject(
        "SELECT xp_amount FROM xp_ledger WHERE user_id = ? AND type = 'COMMENT' AND idempotency_key = ?",
        Integer.class,
        userId,
        "comment:create:" + targetCommentId);
  }

  @BeforeEach
  void setUp() throws Exception {
    baseUrl = "http://localhost:" + port;

    String email = uniqueEmail();
    signup(email, "Test@1234!", "comment-e2e-user");
    accessToken = loginAndGetAccessToken(email, "Test@1234!");
    postId = createFreePost("post for comment e2e");
  }

  @Test
  @Order(1)
  @DisplayName("comment creation returns a persisted comment id")
  void createComment_success_returnsCommentId() throws Exception {
    Map<String, Object> body = Map.of("content", "first comment");
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/posts/" + postId + "/comments",
            HttpMethod.POST,
            new HttpEntity<>(body, authHeaders()),
            String.class);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data/commentId").asLong()).isPositive();
    assertThat(root.at("/data/content").asText()).isEqualTo("first comment");
  }

  @Test
  @Order(2)
  @DisplayName("successful comment creation grants COMMENT XP 1")
  void createComment_success_grantsCommentXp() throws Exception {
    Long userId = currentUserId();
    Long createdCommentId = createComment(postId, "xp verification comment", null);

    assertThat(countCommentXpLedger(userId, createdCommentId)).isEqualTo(1);
    assertThat(loadCommentXpAmount(userId, createdCommentId)).isEqualTo(1);
  }

  @Test
  @Order(3)
  @DisplayName("root comment query includes the created comment")
  void getRootComments_afterCreate_includesComment() throws Exception {
    String content = "comment-" + UUID.randomUUID().toString().substring(0, 8);
    createComment(postId, content, null);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/posts/" + postId + "/comments",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    JsonNode comments = root.at("/data/content");
    assertThat(comments.isArray()).isTrue();
    boolean found = false;
    for (JsonNode comment : comments) {
      if (comment.at("/content").asText().equals(content)) {
        found = true;
        break;
      }
    }
    assertThat(found).isTrue();
  }

  @Test
  @Order(4)
  @DisplayName("updating a comment returns the changed content")
  void updateComment_success_returnsUpdatedContent() throws Exception {
    commentId = createComment(postId, "comment before update", null);

    Map<String, Object> updateBody = Map.of("content", "comment after update");
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/comments/" + commentId,
            HttpMethod.PUT,
            new HttpEntity<>(updateBody, authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data/content").asText()).isEqualTo("comment after update");
  }

  @Test
  @Order(5)
  @DisplayName("deleting a comment returns success")
  void deleteComment_success_returns200() throws Exception {
    commentId = createComment(postId, "comment to delete", null);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/comments/" + commentId,
            HttpMethod.DELETE,
            new HttpEntity<>(authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
  }

  @Test
  @Order(6)
  @DisplayName("reply creation with parentId succeeds")
  void createReply_withParentId_success() throws Exception {
    commentId = createComment(postId, "parent comment", null);

    Map<String, Object> replyBody = Map.of("content", "reply content", "parentId", commentId);
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/posts/" + postId + "/comments",
            HttpMethod.POST,
            new HttpEntity<>(replyBody, authHeaders()),
            String.class);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data/commentId").asLong()).isPositive();
    assertThat(root.at("/data/content").asText()).isEqualTo("reply content");
  }

  @Test
  @Order(7)
  @DisplayName("reply query includes the created reply")
  void getReplies_afterCreateReply_includesReply() throws Exception {
    commentId = createComment(postId, "parent for reply query", null);
    createComment(postId, "reply for query", commentId);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/comments/" + commentId + "/replies",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    JsonNode replies = root.at("/data/content");
    assertThat(replies.isArray()).isTrue();
    assertThat(replies.size()).isGreaterThanOrEqualTo(1);
  }

  @Test
  @Order(8)
  @DisplayName("unauthenticated comment creation returns 401")
  void createComment_withoutAuth_returns401() {
    HttpHeaders noAuthHeaders = new HttpHeaders();
    noAuthHeaders.setContentType(MediaType.APPLICATION_JSON);
    Map<String, Object> body = Map.of("content", "unauthenticated comment");

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/posts/" + postId + "/comments",
            HttpMethod.POST,
            new HttpEntity<>(body, noAuthHeaders),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @Order(9)
  @DisplayName("blank comment content returns 400 and does not grant XP")
  void createComment_withBlankContent_returns400() {
    Map<String, Object> body = Map.of("content", "");

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/posts/" + postId + "/comments",
            HttpMethod.POST,
            new HttpEntity<>(body, authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM xp_ledger WHERE user_id = ? AND type = 'COMMENT'",
                Integer.class,
                currentUserId()))
        .isZero();
  }

  @Test
  @Order(10)
  @DisplayName("updating another user's comment returns a client error")
  void updateComment_byOtherUser_returnsError() throws Exception {
    commentId = createComment(postId, "comment owned by original user", null);

    String otherEmail = uniqueEmail();
    signup(otherEmail, "Test@1234!", "other-user");
    String otherToken = loginAndGetAccessToken(otherEmail, "Test@1234!");

    HttpHeaders otherHeaders = new HttpHeaders();
    otherHeaders.setContentType(MediaType.APPLICATION_JSON);
    otherHeaders.setBearerAuth(otherToken);

    Map<String, Object> updateBody = Map.of("content", "intruder update");
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/comments/" + commentId,
            HttpMethod.PUT,
            new HttpEntity<>(updateBody, otherHeaders),
            String.class);

    assertThat(response.getStatusCode().is4xxClientError()).isTrue();
  }

  @Test
  @Order(11)
  @DisplayName("querying comments for a missing post returns 404")
  void getRootComments_missingPost_returns404() {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/posts/999999999/comments",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
