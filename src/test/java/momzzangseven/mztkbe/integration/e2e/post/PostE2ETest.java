package momzzangseven.mztkbe.integration.e2e.post;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Post CRUD E2E 테스트 (Local Server + Real PostgreSQL).
 *
 * <p>실행 조건:
 *
 * <ul>
 *   <li>로컬 PostgreSQL 서버 실행 필요 (docker compose up -d)
 *   <li>./gradlew e2eTest 명령어로 실행
 * </ul>
 *
 * <p>데이터 격리 전략:
 *
 * <ul>
 *   <li>{@code @BeforeEach}: 테스트마다 UUID 기반 고유 이메일로 신규 유저 생성 → 데이터 충돌 방지
 *   <li>{@code @AfterEach}: JdbcTemplate 으로 생성된 post_tags → posts 순서로 삭제 (FK 고려)
 *   <li>유저 데이터는 XP·출석 등 연관 레코드 FK 충돌 가능성이 있으므로 삭제하지 않음 (고유 이메일로 격리)
 * </ul>
 */
@Tag("e2e")
@ActiveProfiles("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("[E2E] Post CRUD 전체 흐름 테스트")
class PostE2ETest {

  // ============================================================
  // 인프라 주입
  // ============================================================

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  @MockBean private KakaoAuthPort kakaoAuthPort;
  @MockBean private GoogleAuthPort googleAuthPort;
  @MockBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  // ============================================================
  // 테스트 상태 (인스턴스별 독립)
  // ============================================================

  private String baseUrl;
  private String accessToken;
  private final List<Long> createdPostIds = new ArrayList<>();
  private List<Long> imageIds = new ArrayList<>();

  // ============================================================
  // Setup / Teardown
  // ============================================================

  @BeforeEach
  void setUp() throws Exception {
    baseUrl = "http://localhost:" + port;
    createdPostIds.clear();

    String email = uniqueEmail();
    signupUser(email, "Test@1234!", "E2E유저");
    accessToken = loginAndGetToken(email, "Test@1234!");
  }

  /**
   * 테스트에서 생성한 게시글을 DB에서 직접 삭제한다.
   *
   * <p>삭제 순서: post_tags(FK 선행) → posts. RANDOM_PORT 환경에서는 {@code @Transactional} 롤백이 동작하지 않으므로
   * JdbcTemplate 으로 명시적 클린업한다.
   */
  @AfterEach
  void tearDown() {
    for (Long postId : createdPostIds) {
      try {
        jdbcTemplate.update("DELETE FROM post_tags WHERE post_id = ?", postId);
      } catch (Exception ignored) {
        // post_tags 가 없거나 cascade 로 이미 삭제된 경우 무시
      }
      try {
        jdbcTemplate.update("DELETE FROM posts WHERE id = ?", postId);
      } catch (Exception ignored) {
        // 이미 삭제된 게시글(삭제 시나리오 테스트) 무시
      }
    }
    createdPostIds.clear();
  }

  // ============================================================
  // 공통 Helper
  // ============================================================

  private static String uniqueEmail() {
    return "e2e-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@test.com";
  }

  private HttpHeaders authHeaders() {
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    h.setBearerAuth(accessToken);
    return h;
  }

  private HttpHeaders headersWithToken(String token) {
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    h.setBearerAuth(token);
    return h;
  }

  private HttpHeaders noAuthHeaders() {
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    return h;
  }

  private void signupUser(String email, String password, String nickname) {
    Map<String, String> body = Map.of("email", email, "password", password, "nickname", nickname);
    restTemplate.exchange(
        baseUrl + "/auth/signup",
        HttpMethod.POST,
        new HttpEntity<>(body, noAuthHeaders()),
        String.class);
  }

  private String loginAndGetToken(String email, String password) throws Exception {
    Map<String, Object> body = Map.of("provider", "LOCAL", "email", email, "password", password);
    ResponseEntity<String> res =
        restTemplate.exchange(
            baseUrl + "/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(body, noAuthHeaders()),
            String.class);
    assertThat(res.getStatusCode().is2xxSuccessful())
        .as("Login should succeed for email: " + email)
        .isTrue();
    return objectMapper.readTree(res.getBody()).at("/data/accessToken").asText();
  }

  private Long createFreePost(String content) throws Exception {
    Map<String, Object> body = Map.of("content", content, "imageIds", imageIds);
    ResponseEntity<String> res =
        restTemplate.exchange(
            baseUrl + "/posts/free",
            HttpMethod.POST,
            new HttpEntity<>(body, authHeaders()),
            String.class);
    assertThat(res.getStatusCode())
        .as("Free post creation should return 201")
        .isEqualTo(HttpStatus.CREATED);
    Long postId = objectMapper.readTree(res.getBody()).at("/data/postId").asLong();
    createdPostIds.add(postId);
    return postId;
  }

  private Long createQuestionPost(String title, String content, long reward) throws Exception {
    Map<String, Object> body = Map.of("title", title, "content", content, "reward", reward);
    ResponseEntity<String> res =
        restTemplate.exchange(
            baseUrl + "/posts/question",
            HttpMethod.POST,
            new HttpEntity<>(body, authHeaders()),
            String.class);
    assertThat(res.getStatusCode())
        .as("Question post creation should return 201")
        .isEqualTo(HttpStatus.CREATED);
    Long postId = objectMapper.readTree(res.getBody()).at("/data/postId").asLong();
    createdPostIds.add(postId);
    return postId;
  }

  /** isSolved=true 는 web3 transfer 완료 이벤트로만 변경되므로 테스트 전제 조건 설정에 JdbcTemplate 사용. */
  private void markPostAsSolved(Long postId) {
    int updated = jdbcTemplate.update("UPDATE posts SET is_solved = true WHERE id = ?", postId);
    assertThat(updated).as("Post %d should exist to be marked as solved", postId).isEqualTo(1);
  }

  private boolean postExistsInDb(Long postId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM posts WHERE id = ?", Integer.class, postId);
    return count != null && count > 0;
  }

  private String getPostContentFromDb(Long postId) {
    return jdbcTemplate.queryForObject(
        "SELECT content FROM posts WHERE id = ?", String.class, postId);
  }

  private JsonNode parse(ResponseEntity<String> res) throws Exception {
    return objectMapper.readTree(res.getBody());
  }

  // ============================================================
  // FREE 게시글 기본 CRUD (기존 + 강화)
  // ============================================================

  @Test
  @DisplayName("자유 게시글 작성 → 201 응답 및 postId 반환")
  void createFreePost_success_returns201WithPostId() throws Exception {
    // given
    Map<String, Object> body = Map.of("content", "E2E 자유 게시글 내용", "imageIds", imageIds);

    // when
    ResponseEntity<String> res =
        restTemplate.exchange(
            baseUrl + "/posts/free",
            HttpMethod.POST,
            new HttpEntity<>(body, authHeaders()),
            String.class);

    // then
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    JsonNode root = parse(res);
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    long postId = root.at("/data/postId").asLong();
    assertThat(postId).isPositive();
    createdPostIds.add(postId);
  }

  @Test
  @DisplayName("자유 게시글 수정 → 200 응답 및 DB content 변경 확인")
  void updateFreePost_success_and_contentVerifiedInDb() throws Exception {
    // given
    Long postId = createFreePost("수정 전 내용");

    // when
    Map<String, Object> updateBody = Map.of("content", "수정 후 내용 E2E", "imageIds", imageIds);
    ResponseEntity<String> res =
        restTemplate.exchange(
            baseUrl + "/posts/" + postId,
            HttpMethod.PATCH,
            new HttpEntity<>(updateBody, authHeaders()),
            String.class);

    // then - HTTP 응답
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = parse(res);
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data/postId").asLong()).isEqualTo(postId);

    // then - DB 직접 검증
    String savedContent = getPostContentFromDb(postId);
    assertThat(savedContent).isEqualTo("수정 후 내용 E2E");
  }

  @Test
  @DisplayName("자유 게시글 작성 시 duplicate imageIds → 400 BAD_REQUEST")
  void createFreePost_duplicateImageIds_returns400() throws Exception {
    Map<String, Object> body = Map.of("content", "중복 이미지", "imageIds", List.of(1, 1));

    ResponseEntity<String> res =
        restTemplate.exchange(
            baseUrl + "/posts/free",
            HttpMethod.POST,
            new HttpEntity<>(body, authHeaders()),
            String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("자유 게시글 수정 시 duplicate imageIds → 400 BAD_REQUEST")
  void updateFreePost_duplicateImageIds_returns400() throws Exception {
    Long postId = createFreePost("중복 수정 테스트");
    Map<String, Object> body = Map.of("imageIds", List.of(1, 1));

    ResponseEntity<String> res =
        restTemplate.exchange(
            baseUrl + "/posts/" + postId,
            HttpMethod.PATCH,
            new HttpEntity<>(body, authHeaders()),
            String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("자유 게시글 삭제 → 200 응답 및 DB에서 행 제거 확인")
  void deleteFreePost_success_and_removedFromDb() throws Exception {
    // given
    Long postId = createFreePost("삭제될 게시글");

    // when
    ResponseEntity<String> res =
        restTemplate.exchange(
            baseUrl + "/posts/" + postId,
            HttpMethod.DELETE,
            new HttpEntity<>(authHeaders()),
            String.class);

    // then - HTTP 응답
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(parse(res).at("/status").asText()).isEqualTo("SUCCESS");

    // then - DB 직접 검증: 행이 존재하지 않아야 함
    assertThat(postExistsInDb(postId))
        .as("Deleted post should not exist in DB: postId=" + postId)
        .isFalse();
  }

  @Test
  @DisplayName("게시글 상세 조회 → postId, content, writer 포함")
  void getPost_success_returnsPostDetails() throws Exception {
    // given
    Long postId = createFreePost("상세 조회 테스트 게시글");

    // when
    ResponseEntity<String> res =
        restTemplate.exchange(
            baseUrl + "/posts/" + postId,
            HttpMethod.GET,
            new HttpEntity<>(authHeaders()),
            String.class);

    // then
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = parse(res);
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data/postId").asLong()).isEqualTo(postId);
    assertThat(root.at("/data/content").asText()).isEqualTo("상세 조회 테스트 게시글");
    assertThat(root.at("/data/writer/userId").asLong()).isPositive();
  }

  @Test
  @DisplayName("게시글 목록 조회 → FREE 타입 게시글이 목록에 포함")
  void getPosts_afterCreate_includesPost() throws Exception {
    // given
    createFreePost("목록 조회 E2E 게시글");

    // when
    ResponseEntity<String> res =
        restTemplate.exchange(
            baseUrl + "/posts?type=FREE",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders()),
            String.class);

    // then
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = parse(res);
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data").isArray()).isTrue();
    assertThat(root.at("/data").get(0).get("content").asText()).isEqualTo("목록 조회 E2E 게시글");
  }

  @Test
  @DisplayName("타인의 게시글 수정 시도 → 403 FORBIDDEN (POST_002)")
  void updateFreePost_byOtherUser_returns403_withPost002() throws Exception {
    // given: 원래 유저가 게시글 작성
    Long postId = createFreePost("원본 게시글 (타인 수정 시도)");

    // given: 다른 유저 생성 및 로그인
    String otherEmail = uniqueEmail();
    signupUser(otherEmail, "Test@1234!", "타인유저");
    String otherToken = loginAndGetToken(otherEmail, "Test@1234!");

    // when: 다른 유저로 수정 시도
    Map<String, Object> updateBody = Map.of("content", "타인이 수정한 내용");
    ResponseEntity<String> res =
        restTemplate.exchange(
            baseUrl + "/posts/" + postId,
            HttpMethod.PATCH,
            new HttpEntity<>(updateBody, headersWithToken(otherToken)),
            String.class);

    // then
    assertThat(res.getStatusCode())
        .as("Other user should not be able to update the post")
        .isEqualTo(HttpStatus.FORBIDDEN);
    JsonNode root = parse(res);
    assertThat(root.at("/status").asText()).isEqualTo("FAIL");
    assertThat(root.at("/code").asText()).isEqualTo("POST_002");
  }

  @Test
  @DisplayName("타인의 게시글 삭제 시도 → 403 FORBIDDEN (POST_002)")
  void deleteFreePost_byOtherUser_returns403_withPost002() throws Exception {
    // given: 원래 유저가 게시글 작성
    Long postId = createFreePost("원본 게시글 (타인 삭제 시도)");

    // given: 다른 유저 생성 및 로그인
    String otherEmail = uniqueEmail();
    signupUser(otherEmail, "Test@1234!", "삭제시도유저");
    String otherToken = loginAndGetToken(otherEmail, "Test@1234!");

    // when: 다른 유저로 삭제 시도
    ResponseEntity<String> res =
        restTemplate.exchange(
            baseUrl + "/posts/" + postId,
            HttpMethod.DELETE,
            new HttpEntity<>(headersWithToken(otherToken)),
            String.class);

    // then
    assertThat(res.getStatusCode())
        .as("Other user should not be able to delete the post")
        .isEqualTo(HttpStatus.FORBIDDEN);
    JsonNode root = parse(res);
    assertThat(root.at("/status").asText()).isEqualTo("FAIL");
    assertThat(root.at("/code").asText()).isEqualTo("POST_002");
  }

  @Test
  @DisplayName("내용 없이 게시글 작성 시 → 400 BAD_REQUEST (VALIDATION_001)")
  void createFreePost_withBlankContent_returns400() throws Exception {
    // given: content 필드 빈 문자열
    Map<String, Object> body = Map.of("content", "");

    // when
    ResponseEntity<String> res =
        restTemplate.exchange(
            baseUrl + "/posts/free",
            HttpMethod.POST,
            new HttpEntity<>(body, authHeaders()),
            String.class);

    // then
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(parse(res).at("/code").asText()).isEqualTo("VALIDATION_001");
  }

  @Test
  @DisplayName("토큰 없이 게시글 작성 시 → 401 UNAUTHORIZED (AUTH_006)")
  void createFreePost_withoutToken_returns401_withAuth006() {
    // given: 인증 헤더 없음
    Map<String, Object> body = Map.of("content", "비인증 게시글");

    // when
    ResponseEntity<String> res =
        restTemplate.exchange(
            baseUrl + "/posts/free",
            HttpMethod.POST,
            new HttpEntity<>(body, noAuthHeaders()),
            String.class);

    // then: Spring Security 필터에서 차단 → RestAuthenticationEntryPoint
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    try {
      JsonNode root = parse(res);
      assertThat(root.at("/code").asText()).isEqualTo("AUTH_006");
    } catch (Exception ignored) {
      // 응답 바디가 없는 경우에도 상태코드만으로 통과
    }
  }

  // ============================================================
  // QUESTION 게시글 생성
  // ============================================================

  @Nested
  @DisplayName("QUESTION 게시글 생성")
  class CreateQuestionPost {

    @Test
    @DisplayName("유효한 title·content·reward → 201 응답 및 DB 저장 확인")
    void createQuestion_success_returns201_and_savedInDb() throws Exception {
      // given
      String title = "E2E 질문 제목";
      String content = "E2E 질문 내용입니다.";
      long reward = 50L;

      // when
      Long postId = createQuestionPost(title, content, reward);

      // then - HTTP 응답은 createQuestionPost helper 내부에서 검증됨
      assertThat(postId).isPositive();

      // then - DB 직접 검증
      Map<String, Object> row =
          jdbcTemplate.queryForMap("SELECT * FROM posts WHERE id = ?", postId);
      assertThat(row.get("title")).isEqualTo(title);
      assertThat(row.get("content")).isEqualTo(content);
      assertThat(((Number) row.get("reward")).longValue()).isEqualTo(reward);
      assertThat(row.get("type").toString()).isEqualTo("QUESTION");
      assertThat((Boolean) row.get("is_solved")).isFalse();
    }

    @Test
    @DisplayName("reward = 0 → 400 BAD_REQUEST")
    void createQuestion_rewardIsZero_returns400_badRequest() throws Exception {
      // given: reward=0 은 request validation/API command/domain 어느 경로에서도 허용되지 않음
      Map<String, Object> body = Map.of("title", "질문 제목", "content", "질문 내용", "reward", 0);

      // when
      ResponseEntity<String> res =
          restTemplate.exchange(
              baseUrl + "/posts/question",
              HttpMethod.POST,
              new HttpEntity<>(body, authHeaders()),
              String.class);

      // then
      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("reward = -5 → 400 (VALIDATION_001 or POST_003)")
    void createQuestion_rewardIsNegative_returns400() throws Exception {
      // given: @Positive Bean Validation 또는 커맨드 검증에서 차단
      Map<String, Object> body = Map.of("title", "질문 제목", "content", "질문 내용", "reward", -5);

      // when
      ResponseEntity<String> res =
          restTemplate.exchange(
              baseUrl + "/posts/question",
              HttpMethod.POST,
              new HttpEntity<>(body, authHeaders()),
              String.class);

      // then
      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("title 누락 → 400 BAD_REQUEST (VALIDATION_001)")
    void createQuestion_missingTitle_returns400_validation001() throws Exception {
      // given: @NotBlank title 미포함
      Map<String, Object> body = Map.of("content", "질문 내용", "reward", 10);

      // when
      ResponseEntity<String> res =
          restTemplate.exchange(
              baseUrl + "/posts/question",
              HttpMethod.POST,
              new HttpEntity<>(body, authHeaders()),
              String.class);

      // then
      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(parse(res).at("/code").asText()).isEqualTo("VALIDATION_001");
    }

    @Test
    @DisplayName("content 누락 → 400 BAD_REQUEST (VALIDATION_001)")
    void createQuestion_missingContent_returns400_validation001() throws Exception {
      // given: @NotBlank content 미포함
      Map<String, Object> body = Map.of("title", "질문 제목", "reward", 10);

      // when
      ResponseEntity<String> res =
          restTemplate.exchange(
              baseUrl + "/posts/question",
              HttpMethod.POST,
              new HttpEntity<>(body, authHeaders()),
              String.class);

      // then
      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(parse(res).at("/code").asText()).isEqualTo("VALIDATION_001");
    }

    @Test
    @DisplayName("reward 누락 → 400 BAD_REQUEST (VALIDATION_001)")
    void createQuestion_missingReward_returns400_validation001() throws Exception {
      // given: @NotNull reward 미포함
      Map<String, Object> body = Map.of("title", "질문 제목", "content", "질문 내용");

      // when
      ResponseEntity<String> res =
          restTemplate.exchange(
              baseUrl + "/posts/question",
              HttpMethod.POST,
              new HttpEntity<>(body, authHeaders()),
              String.class);

      // then
      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(parse(res).at("/code").asText()).isEqualTo("VALIDATION_001");
    }

    @Test
    @DisplayName("토큰 없이 요청 → 401 UNAUTHORIZED (AUTH_006)")
    void createQuestion_withoutToken_returns401_auth006() throws Exception {
      // given: 인증 헤더 없음
      Map<String, Object> body = Map.of("title", "질문 제목", "content", "질문 내용", "reward", 10);

      // when
      ResponseEntity<String> res =
          restTemplate.exchange(
              baseUrl + "/posts/question",
              HttpMethod.POST,
              new HttpEntity<>(body, noAuthHeaders()),
              String.class);

      // then: RestAuthenticationEntryPoint → USER_NOT_AUTHENTICATED
      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
      assertThat(parse(res).at("/code").asText()).isEqualTo("AUTH_006");
    }
  }

  // ============================================================
  // QUESTION 게시글 수정
  // ============================================================

  @Nested
  @DisplayName("QUESTION 게시글 수정")
  class UpdateQuestionPost {

    @Test
    @DisplayName("소유자가 수정 → 200 응답 및 DB content 변경 확인")
    void updateQuestion_byOwner_success_and_contentVerifiedInDb() throws Exception {
      // given
      Long postId = createQuestionPost("원본 질문 제목", "원본 질문 내용", 30L);

      // when
      Map<String, Object> updateBody = Map.of("content", "수정된 질문 내용 (E2E 검증)");
      ResponseEntity<String> res =
          restTemplate.exchange(
              baseUrl + "/posts/" + postId,
              HttpMethod.PATCH,
              new HttpEntity<>(updateBody, authHeaders()),
              String.class);

      // then - HTTP 응답
      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode root = parse(res);
      assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
      assertThat(root.at("/data/postId").asLong()).isEqualTo(postId);

      // then - DB 직접 검증
      String savedContent = getPostContentFromDb(postId);
      assertThat(savedContent)
          .as("DB should reflect updated content for postId=%d", postId)
          .isEqualTo("수정된 질문 내용 (E2E 검증)");
    }

    @Test
    @DisplayName("타인이 수정 시도 → 403 FORBIDDEN (POST_002)")
    void updateQuestion_byOtherUser_returns403_withPost002() throws Exception {
      // given: 원래 유저 게시글
      Long postId = createQuestionPost("타인수정테스트 질문", "질문 내용", 20L);

      // given: 다른 유저
      String otherEmail = uniqueEmail();
      signupUser(otherEmail, "Test@1234!", "타인유저");
      String otherToken = loginAndGetToken(otherEmail, "Test@1234!");

      // when
      Map<String, Object> updateBody = Map.of("content", "무단 수정 시도");
      ResponseEntity<String> res =
          restTemplate.exchange(
              baseUrl + "/posts/" + postId,
              HttpMethod.PATCH,
              new HttpEntity<>(updateBody, headersWithToken(otherToken)),
              String.class);

      // then
      assertThat(res.getStatusCode())
          .as("Other user update attempt should be forbidden")
          .isEqualTo(HttpStatus.FORBIDDEN);
      JsonNode root = parse(res);
      assertThat(root.at("/status").asText()).isEqualTo("FAIL");
      assertThat(root.at("/code").asText()).isEqualTo("POST_002");
    }

    @Test
    @DisplayName("isSolved=true 인 게시글 수정 시도 → 400 BAD_REQUEST (POST_003)")
    void updateQuestion_whenSolved_returns400_withPost003() throws Exception {
      // given: 질문 게시글 생성 후 solved 상태로 강제 설정 (JdbcTemplate)
      Long postId = createQuestionPost("해결됨 수정불가 질문", "질문 내용", 100L);
      markPostAsSolved(postId);

      // when: 소유자가 수정 시도 (isSolved=true 이므로 도메인에서 거부)
      Map<String, Object> updateBody = Map.of("content", "해결된 게시글 수정 시도");
      ResponseEntity<String> res =
          restTemplate.exchange(
              baseUrl + "/posts/" + postId,
              HttpMethod.PATCH,
              new HttpEntity<>(updateBody, authHeaders()),
              String.class);

      // then: Post.update() 내 도메인 불변식 → PostInvalidInputException → POST_003
      assertThat(res.getStatusCode())
          .as("Updating a solved question post should be rejected")
          .isEqualTo(HttpStatus.BAD_REQUEST);
      JsonNode root = parse(res);
      assertThat(root.at("/status").asText()).isEqualTo("FAIL");
      assertThat(root.at("/code").asText()).isEqualTo("POST_003");
    }
  }

  // ============================================================
  // QUESTION 게시글 삭제
  // ============================================================

  @Nested
  @DisplayName("QUESTION 게시글 삭제")
  class DeleteQuestionPost {

    @Test
    @DisplayName("소유자가 삭제 → 200 응답 및 DB에서 행 제거 확인")
    void deleteQuestion_byOwner_success_and_removedFromDb() throws Exception {
      // given
      Long postId = createQuestionPost("삭제될 질문 제목", "삭제될 질문 내용", 40L);

      // when
      ResponseEntity<String> res =
          restTemplate.exchange(
              baseUrl + "/posts/" + postId,
              HttpMethod.DELETE,
              new HttpEntity<>(authHeaders()),
              String.class);

      // then - HTTP 응답
      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(parse(res).at("/status").asText()).isEqualTo("SUCCESS");

      // then - DB 직접 검증: 행이 존재하지 않아야 함
      assertThat(postExistsInDb(postId))
          .as("Deleted question post should not exist in DB: postId=%d", postId)
          .isFalse();
    }

    @Test
    @DisplayName("타인이 삭제 시도 → 403 FORBIDDEN (POST_002)")
    void deleteQuestion_byOtherUser_returns403_withPost002() throws Exception {
      // given: 원래 유저 게시글
      Long postId = createQuestionPost("타인삭제테스트 질문", "질문 내용", 15L);

      // given: 다른 유저
      String otherEmail = uniqueEmail();
      signupUser(otherEmail, "Test@1234!", "삭제시도유저");
      String otherToken = loginAndGetToken(otherEmail, "Test@1234!");

      // when
      ResponseEntity<String> res =
          restTemplate.exchange(
              baseUrl + "/posts/" + postId,
              HttpMethod.DELETE,
              new HttpEntity<>(headersWithToken(otherToken)),
              String.class);

      // then
      assertThat(res.getStatusCode())
          .as("Other user delete attempt should be forbidden")
          .isEqualTo(HttpStatus.FORBIDDEN);
      JsonNode root = parse(res);
      assertThat(root.at("/status").asText()).isEqualTo("FAIL");
      assertThat(root.at("/code").asText()).isEqualTo("POST_002");
    }

    @Test
    @DisplayName("isSolved=true 인 게시글 삭제 시도 → 400 BAD_REQUEST (POST_003)")
    void deleteQuestion_whenSolved_returns400_withPost003() throws Exception {
      // given: 질문 게시글 생성 후 solved 상태로 강제 설정 (JdbcTemplate)
      Long postId = createQuestionPost("해결됨 삭제불가 질문", "질문 내용", 80L);
      markPostAsSolved(postId);

      // when: 소유자가 삭제 시도
      ResponseEntity<String> res =
          restTemplate.exchange(
              baseUrl + "/posts/" + postId,
              HttpMethod.DELETE,
              new HttpEntity<>(authHeaders()),
              String.class);

      // then: Post.validateDeletable() → PostInvalidInputException → POST_003
      assertThat(res.getStatusCode())
          .as("Deleting a solved question post should be rejected")
          .isEqualTo(HttpStatus.BAD_REQUEST);
      JsonNode root = parse(res);
      assertThat(root.at("/status").asText()).isEqualTo("FAIL");
      assertThat(root.at("/code").asText()).isEqualTo("POST_003");
    }
  }
}
