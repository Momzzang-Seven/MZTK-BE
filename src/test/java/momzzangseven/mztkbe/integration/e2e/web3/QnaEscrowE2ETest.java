package momzzangseven.mztkbe.integration.e2e.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraft;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.BuildQnaExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.PrecheckQuestionFundingPort;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.BDDMockito;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * QnA Escrow 전체 흐름 E2E 테스트 (Local Server + Real PostgreSQL).
 *
 * <p>실행 조건:
 *
 * <ul>
 *   <li>로컬 PostgreSQL 서버 실행 필요 (docker compose up -d)
 *   <li>./gradlew e2eTest 명령어로 실행
 *   <li>web3.reward-token.enabled=true 및 web3.eip7702.enabled=true 설정
 * </ul>
 *
 * <p>블록체인 외부 호출 차단 전략:
 *
 * <ul>
 *   <li>{@code BuildQnaExecutionDraftPort} — ABI 인코딩 + 컨트랙트 호출 계층을 Mock 으로 대체
 *   <li>{@code PrecheckQuestionFundingPort} — 토큰 승인 잔액 검사를 no-op Mock 으로 대체
 *   <li>DB 저장 ({@code CreateExecutionIntentUseCase}) 은 실제 PostgreSQL 로 동작
 * </ul>
 *
 * <p>테스트 시나리오:
 *
 * <ul>
 *   <li>질문 작성 → execution intent 가 DB 에 AWAITING_SIGNATURE 상태로 생성되는지 확인
 *   <li>GET /users/me/web3/execution-intents/{id} 로 intent 조회 및 응답 구조 검증
 * </ul>
 */
@Tag("e2e")
@ActiveProfiles("integration")
@TestPropertySource(
    properties = {
      "web3.reward-token.enabled=true",
      "web3.eip7702.enabled=true",
      "web3.eip7702.sponsor.enabled=true",
      "web3.eip7702.sponsor.per-tx-cap-eth=0.1",
      "web3.eip7702.sponsor.per-day-user-cap-eth=1.0"
    })
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("[E2E] QnA Escrow 전체 흐름 테스트")
class QnaEscrowE2ETest {

  private static final String FAKE_DELEGATE_TARGET = "0x0000000000000000000000000000000000000001";
  private static final String FAKE_CALL_TARGET = "0x0000000000000000000000000000000000000002";

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  /** ABI 인코딩 + 온체인 prevalidation 계층을 Mock 으로 대체 */
  @MockitoBean private BuildQnaExecutionDraftPort buildQnaExecutionDraftPort;

  /** 토큰 승인 잔액 확인 계층을 no-op Mock 으로 대체 */
  @MockitoBean private PrecheckQuestionFundingPort precheckQuestionFundingPort;

  private String baseUrl;
  private String accessToken;
  private String currentUserEmail;
  private Long createdPostId;

  // ── setup / teardown ───────────────────────────────────────────────────────

  @BeforeEach
  void setUp() throws Exception {
    baseUrl = "http://localhost:" + port;
    currentUserEmail = "e2e-qna-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    signup(currentUserEmail, "Test@1234!", "QnAEscrow유저");
    accessToken = loginAndGetToken(currentUserEmail, "Test@1234!");

    BDDMockito.given(buildQnaExecutionDraftPort.build(any()))
        .willAnswer(
            inv -> {
              momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionRequest req =
                  inv.getArgument(0);
              return new QnaExecutionDraft(
                  QnaExecutionResourceType.QUESTION,
                  req.resourceId(),
                  QnaExecutionResourceStatus.PENDING_EXECUTION,
                  QnaExecutionActionType.QNA_QUESTION_CREATE,
                  req.requesterUserId(),
                  req.counterpartyUserId(),
                  "root-" + req.resourceId(),
                  "0x" + "a".repeat(64),
                  "{}",
                  List.of(
                      new QnaExecutionDraftCall(FAKE_CALL_TARGET, BigInteger.ZERO, "0x1234abcd")),
                  true,
                  "0x" + "f".repeat(40),
                  0L,
                  FAKE_DELEGATE_TARGET,
                  "0x" + "b".repeat(64),
                  null,
                  "0x" + "c".repeat(64),
                  LocalDateTime.now().plusMinutes(30));
            });

    BDDMockito.doNothing().when(precheckQuestionFundingPort).precheck(any());
  }

  @AfterEach
  void tearDown() {
    if (createdPostId != null) {
      jdbcTemplate.update("DELETE FROM web3_execution_intents WHERE resource_id = ?", createdPostId.toString());
      jdbcTemplate.update("DELETE FROM post_tags WHERE post_id = ?", createdPostId);
      jdbcTemplate.update("DELETE FROM posts WHERE id = ?", createdPostId);
    }
  }

  // ── test cases ─────────────────────────────────────────────────────────────

  @Test
  @Order(1)
  @DisplayName("질문 작성 시 execution intent 가 AWAITING_SIGNATURE 상태로 DB 에 저장된다")
  void createQuestionPost_persistsExecutionIntentInAwaitingSignatureState() throws Exception {
    ResponseEntity<String> response = createQuestionPost("E2E 에스크로 테스트 질문", "질문 본문", 50L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.path("status").asText()).isEqualTo("SUCCESS");

    createdPostId = body.path("data").path("postId").asLong();
    assertThat(createdPostId).isPositive();

    String intentStatus =
        jdbcTemplate.queryForObject(
            "SELECT status FROM web3_execution_intents WHERE resource_id = ?",
            String.class,
            createdPostId.toString());
    assertThat(intentStatus).isEqualTo("AWAITING_SIGNATURE");
  }

  @Test
  @Order(2)
  @DisplayName("GET /users/me/web3/execution-intents/{id} 가 AWAITING_SIGNATURE intent 를 올바르게 반환한다")
  void getExecutionIntent_returnsAwaitingSignatureIntentWithSignRequest() throws Exception {
    ResponseEntity<String> createResponse =
        createQuestionPost("E2E GET 인텐트 테스트 질문", "GET 테스트 본문", 30L);
    assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    createdPostId =
        objectMapper.readTree(createResponse.getBody()).path("data").path("postId").asLong();

    String intentPublicId =
        jdbcTemplate.queryForObject(
            "SELECT public_id FROM web3_execution_intents WHERE resource_id = ?",
            String.class,
            createdPostId.toString());
    assertThat(intentPublicId).isNotBlank();

    ResponseEntity<String> getResponse =
        restTemplate.exchange(
            baseUrl + "/users/me/web3/execution-intents/" + intentPublicId,
            HttpMethod.GET,
            new HttpEntity<>(authHeaders()),
            String.class);

    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode data = objectMapper.readTree(getResponse.getBody()).path("data");
    assertThat(data.path("resource").path("type").asText()).isEqualTo("QUESTION");
    assertThat(data.path("executionIntent").path("id").asText()).isEqualTo(intentPublicId);
    assertThat(data.path("executionIntent").path("status").asText())
        .isEqualTo("AWAITING_SIGNATURE");
    assertThat(data.path("execution").path("mode").asText()).isEqualTo("EIP7702");
    assertThat(data.path("execution").path("signCount").asInt()).isEqualTo(2);
    assertThat(data.path("signRequest").isMissingNode()).isFalse();
    assertThat(data.path("signRequest").path("authorization").isMissingNode()).isFalse();
    assertThat(data.path("signRequest").path("submit").isMissingNode()).isFalse();
  }

  @Test
  @Order(3)
  @DisplayName("GET /users/me/web3/execution-intents/{id} — 인증 없으면 401 반환")
  void getExecutionIntent_withoutAuth_returns401() {
    ResponseEntity<String> response =
        restTemplate.getForEntity(
            baseUrl + "/users/me/web3/execution-intents/some-id", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private void signup(String email, String password, String nickname) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    restTemplate.exchange(
        baseUrl + "/auth/signup",
        HttpMethod.POST,
        new HttpEntity<>(Map.of("email", email, "password", password, "nickname", nickname), headers),
        String.class);
  }

  private String loginAndGetToken(String email, String password) throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("provider", "LOCAL", "email", email, "password", password), headers),
            String.class);
    return objectMapper.readTree(response.getBody()).at("/data/accessToken").asText();
  }

  private ResponseEntity<String> createQuestionPost(String title, String content, Long reward) {
    return restTemplate.exchange(
        baseUrl + "/posts/question",
        HttpMethod.POST,
        new HttpEntity<>(
            Map.of("title", title, "content", content, "reward", reward, "tags", List.of()),
            authHeaders()),
        String.class);
  }

  private HttpHeaders authHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(accessToken);
    return headers;
  }
}
