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
 * Comment CRUD E2E 테스트 (Local Server + Real PostgreSQL).
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
 *   <li>자유 게시글 생성 → 댓글 생성 → 조회 → 수정 → 삭제
 *   <li>대댓글(답글) 생성 및 조회
 *   <li>인증 없이 접근 시 401 반환 검증
 * </ul>
 *
 * <p>외부 API(Kakao, Google)는 MockBean으로 대체합니다.
 */
@Tag("e2e")
@ActiveProfiles("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("[E2E] Comment CRUD 전체 흐름 테스트")
class CommentE2ETest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private KakaoAuthPort kakaoAuthPort;
  @MockBean private GoogleAuthPort googleAuthPort;
  @MockBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  private String baseUrl;

  /** 테스트 픽스처 — 각 테스트 클래스 인스턴스마다 고유한 사용자/게시글을 사용한다. */
  private String accessToken;

  private Long postId;
  private Long commentId;

  private List<Long> imageIds = new ArrayList<>();

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
        .as("댓글 생성 응답이 2xx여야 함: " + response.getBody())
        .isTrue();
    JsonNode root = objectMapper.readTree(response.getBody());
    return root.at("/data/commentId").asLong();
  }

  // ============================================================
  // Setup
  // ============================================================

  @BeforeEach
  void setUp() throws Exception {
    baseUrl = "http://localhost:" + port;

    String email = uniqueEmail();
    signup(email, "Test@1234!", "댓글E2E유저");
    accessToken = loginAndGetAccessToken(email, "Test@1234!");
    postId = createFreePost("E2E 테스트용 자유게시글 내용입니다.");
  }

  // ============================================================
  // E2E Tests
  // ============================================================

  @Test
  @Order(1)
  @DisplayName("댓글 생성 → DB 저장 후 commentId 반환")
  void createComment_success_returnsCommentId() throws Exception {
    Map<String, Object> body = Map.of("content", "첫 번째 댓글입니다.");
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
    assertThat(root.at("/data/content").asText()).isEqualTo("첫 번째 댓글입니다.");
  }

  @Test
  @Order(2)
  @DisplayName("댓글 조회 — 생성된 댓글이 목록에 포함됨")
  void getRootComments_afterCreate_includesComment() throws Exception {
    String content = "조회 테스트 댓글_" + UUID.randomUUID().toString().substring(0, 8);
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
    assertThat(found).as("생성한 댓글이 목록에 포함되어야 함").isTrue();
  }

  @Test
  @Order(3)
  @DisplayName("댓글 수정 — content 변경 후 수정된 값 반환")
  void updateComment_success_returnsUpdatedContent() throws Exception {
    commentId = createComment(postId, "수정 전 댓글 내용", null);

    Map<String, Object> updateBody = Map.of("content", "수정 후 댓글 내용");
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/comments/" + commentId,
            HttpMethod.PUT,
            new HttpEntity<>(updateBody, authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data/content").asText()).isEqualTo("수정 후 댓글 내용");
  }

  @Test
  @Order(4)
  @DisplayName("댓글 삭제 — 삭제 후 Soft Delete 처리됨")
  void deleteComment_success_returns200() throws Exception {
    commentId = createComment(postId, "삭제될 댓글 내용", null);

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
  @Order(5)
  @DisplayName("대댓글(답글) 생성 → parentId 포함된 답글 저장")
  void createReply_withParentId_success() throws Exception {
    commentId = createComment(postId, "부모 댓글", null);

    Map<String, Object> replyBody = Map.of("content", "대댓글 내용입니다.", "parentId", commentId);
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
    assertThat(root.at("/data/content").asText()).isEqualTo("대댓글 내용입니다.");
  }

  @Test
  @Order(6)
  @DisplayName("대댓글 목록 조회 — 부모 댓글의 답글 목록 반환")
  void getReplies_afterCreateReply_includesReply() throws Exception {
    commentId = createComment(postId, "부모 댓글 (대댓글 조회 테스트)", null);
    createComment(postId, "조회될 대댓글", commentId);

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
  @Order(7)
  @DisplayName("인증 없이 댓글 생성 시 401 반환")
  void createComment_withoutAuth_returns401() {
    HttpHeaders noAuthHeaders = new HttpHeaders();
    noAuthHeaders.setContentType(MediaType.APPLICATION_JSON);
    Map<String, Object> body = Map.of("content", "비인증 댓글");

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/posts/" + postId + "/comments",
            HttpMethod.POST,
            new HttpEntity<>(body, noAuthHeaders),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @Order(8)
  @DisplayName("댓글 내용이 빈 문자열이면 400 반환")
  void createComment_withBlankContent_returns400() {
    Map<String, Object> body = Map.of("content", "");

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/posts/" + postId + "/comments",
            HttpMethod.POST,
            new HttpEntity<>(body, authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @Order(9)
  @DisplayName("다른 유저가 타인의 댓글 수정 시 에러 반환")
  void updateComment_byOtherUser_returnsError() throws Exception {
    commentId = createComment(postId, "원본 댓글 (타인 수정 시도)", null);

    String otherEmail = uniqueEmail();
    signup(otherEmail, "Test@1234!", "다른유저");
    String otherToken = loginAndGetAccessToken(otherEmail, "Test@1234!");

    HttpHeaders otherHeaders = new HttpHeaders();
    otherHeaders.setContentType(MediaType.APPLICATION_JSON);
    otherHeaders.setBearerAuth(otherToken);

    Map<String, Object> updateBody = Map.of("content", "타인이 수정한 내용");
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/comments/" + commentId,
            HttpMethod.PUT,
            new HttpEntity<>(updateBody, otherHeaders),
            String.class);

    assertThat(response.getStatusCode().is4xxClientError()).as("타인의 댓글 수정 시 4xx 에러여야 함").isTrue();
  }
}
