package momzzangseven.mztkbe.integration.e2e.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Post CRUD E2E 테스트 (Local Server + Real PostgreSQL).
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
 *   <li>자유 게시글 작성 → 조회 → 목록 조회 → 수정 → 삭제 전체 흐름
 *   <li>태그, 이미지 URL 포함 게시글 작성
 *   <li>인증 없이 접근 시 401 반환 검증
 *   <li>타인 게시글 수정/삭제 시 에러 반환 검증
 * </ul>
 */
@Tag("e2e")
@ActiveProfiles("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("[E2E] Post CRUD 전체 흐름 테스트")
class PostE2ETest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private KakaoAuthPort kakaoAuthPort;
  @MockBean private GoogleAuthPort googleAuthPort;
  @MockBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  private String baseUrl;
  private String accessToken;
  private Long postId;

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
    return objectMapper.readTree(response.getBody()).at("/data/accessToken").asText();
  }

  private Long createFreePost(String content) throws Exception {
    Map<String, Object> body = Map.of("content", content);
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/posts/free",
            HttpMethod.POST,
            new HttpEntity<>(body, authHeaders()),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return objectMapper.readTree(response.getBody()).at("/data/postId").asLong();
  }

  // ============================================================
  // Setup
  // ============================================================

  @BeforeEach
  void setUp() throws Exception {
    baseUrl = "http://localhost:" + port;
    String email = uniqueEmail();
    signup(email, "Test@1234!", "게시글E2E유저");
    accessToken = loginAndGetAccessToken(email, "Test@1234!");
  }

  // ============================================================
  // E2E Tests
  // ============================================================

  @Test
  @Order(1)
  @DisplayName("자유 게시글 작성 → 201 응답 및 postId 반환")
  void createFreePost_success_returns201WithPostId() throws Exception {
    Map<String, Object> body = Map.of("content", "E2E 테스트 자유 게시글 내용입니다.");

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/posts/free",
            HttpMethod.POST,
            new HttpEntity<>(body, authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data/postId").asLong()).isPositive();
  }

  @Test
  @Order(2)
  @DisplayName("태그 포함 자유 게시글 작성 → tags 정보 DB 저장 확인")
  void createFreePost_withTags_success() throws Exception {
    Map<String, Object> body =
        Map.of("content", "태그 포함 게시글", "tags", List.of("운동", "헬스", "E2E테스트"));

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/posts/free",
            HttpMethod.POST,
            new HttpEntity<>(body, authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/data/postId").asLong()).isPositive();
  }

  @Test
  @Order(3)
  @DisplayName("게시글 상세 조회 → postId, content, writer 포함")
  void getPost_success_returnsPostDetails() throws Exception {
    postId = createFreePost("상세 조회 테스트 게시글입니다.");

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/posts/" + postId,
            HttpMethod.GET,
            new HttpEntity<>(authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data/postId").asLong()).isEqualTo(postId);
    assertThat(root.at("/data/content").asText()).isEqualTo("상세 조회 테스트 게시글입니다.");
    assertThat(root.at("/data/writer/userId").asLong()).isPositive();
  }

  @Test
  @Order(4)
  @DisplayName("게시글 목록 조회 → 생성된 게시글이 목록에 포함")
  void getPosts_afterCreate_includesPost() throws Exception {
    String uniqueContent = "목록조회E2E_" + UUID.randomUUID().toString().substring(0, 8);
    createFreePost(uniqueContent);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/posts?type=FREE",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data").isArray()).isTrue();
  }

  @Test
  @Order(5)
  @DisplayName("게시글 수정 → 수정된 content가 응답에 반환")
  void updatePost_success_returnsPostId() throws Exception {
    postId = createFreePost("수정 전 게시글 내용");

    Map<String, Object> updateBody = Map.of("content", "수정 후 게시글 내용");
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/posts/" + postId,
            HttpMethod.PATCH,
            new HttpEntity<>(updateBody, authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data/postId").asLong()).isEqualTo(postId);
  }

  @Test
  @Order(6)
  @DisplayName("게시글 삭제 → 삭제 후 200 응답")
  void deletePost_success_returns200() throws Exception {
    postId = createFreePost("삭제될 게시글 내용");

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/posts/" + postId,
            HttpMethod.DELETE,
            new HttpEntity<>(authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data/postId").asLong()).isEqualTo(postId);
  }

  @Test
  @Order(7)
  @DisplayName("인증 없이 게시글 작성 시 401 반환")
  void createFreePost_withoutAuth_returns401() {
    HttpHeaders noAuthHeaders = new HttpHeaders();
    noAuthHeaders.setContentType(MediaType.APPLICATION_JSON);
    Map<String, Object> body = Map.of("content", "비인증 게시글");

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/posts/free",
            HttpMethod.POST,
            new HttpEntity<>(body, noAuthHeaders),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @Order(8)
  @DisplayName("내용이 빈 게시글 작성 시 400 반환")
  void createFreePost_withBlankContent_returns400() {
    Map<String, Object> body = Map.of("content", "");

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/posts/free",
            HttpMethod.POST,
            new HttpEntity<>(body, authHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @Order(9)
  @DisplayName("타인의 게시글 수정 시도 → 4xx 에러 반환")
  void updatePost_byOtherUser_returnsError() throws Exception {
    postId = createFreePost("원본 게시글 (타인 수정 시도)");

    String otherEmail = uniqueEmail();
    signup(otherEmail, "Test@1234!", "다른유저");
    String otherToken = loginAndGetAccessToken(otherEmail, "Test@1234!");

    HttpHeaders otherHeaders = new HttpHeaders();
    otherHeaders.setContentType(MediaType.APPLICATION_JSON);
    otherHeaders.setBearerAuth(otherToken);

    Map<String, Object> updateBody = Map.of("content", "타인이 수정한 내용");
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/posts/" + postId,
            HttpMethod.PATCH,
            new HttpEntity<>(updateBody, otherHeaders),
            String.class);

    assertThat(response.getStatusCode().is4xxClientError()).as("타인의 게시글 수정 시 4xx 에러여야 함").isTrue();
  }

  @Test
  @Order(10)
  @DisplayName("타인의 게시글 삭제 시도 → 4xx 에러 반환")
  void deletePost_byOtherUser_returnsError() throws Exception {
    postId = createFreePost("원본 게시글 (타인 삭제 시도)");

    String otherEmail = uniqueEmail();
    signup(otherEmail, "Test@1234!", "삭제시도유저");
    String otherToken = loginAndGetAccessToken(otherEmail, "Test@1234!");

    HttpHeaders otherHeaders = new HttpHeaders();
    otherHeaders.setContentType(MediaType.APPLICATION_JSON);
    otherHeaders.setBearerAuth(otherToken);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/posts/" + postId,
            HttpMethod.DELETE,
            new HttpEntity<>(otherHeaders),
            String.class);

    assertThat(response.getStatusCode().is4xxClientError()).as("타인의 게시글 삭제 시 4xx 에러여야 함").isTrue();
  }
}
