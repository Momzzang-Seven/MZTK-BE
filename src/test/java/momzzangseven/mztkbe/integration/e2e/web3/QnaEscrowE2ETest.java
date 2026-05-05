package momzzangseven.mztkbe.integration.e2e.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraft;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.BuildQnaExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.PrecheckQuestionFundingPort;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaContentHashFactory;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdCodec;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdempotencyKeyFactory;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
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
@TestPropertySource(
    properties = {
      "web3.reward-token.enabled=true",
      "web3.eip7702.enabled=true",
      "web3.eip7702.sponsor.enabled=true",
      "web3.eip7702.sponsor.per-tx-cap-eth=0.1",
      "web3.eip7702.sponsor.per-day-user-cap-eth=1.0"
    })
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("[E2E] QnA Escrow 전체 흐름 테스트")
class QnaEscrowE2ETest extends E2ETestBase {

  private static final String FAKE_DELEGATE_TARGET = "0x0000000000000000000000000000000000000001";
  private static final String FAKE_CALL_TARGET = "0x0000000000000000000000000000000000000002";

  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  /** ABI 인코딩 + 온체인 prevalidation 계층을 Mock 으로 대체. */
  @MockitoBean private BuildQnaExecutionDraftPort buildQnaExecutionDraftPort;

  /** 토큰 승인 잔액 확인 계층을 no-op Mock 으로 대체. */
  @MockitoBean private PrecheckQuestionFundingPort precheckQuestionFundingPort;

  private String accessToken;
  private Long currentUserId;
  private Long createdPostId;

  @BeforeEach
  void setUp() {
    TestUser user = signupAndLogin("QnAEscrow유저");
    accessToken = user.accessToken();
    currentUserId = user.userId();

    BDDMockito.given(buildQnaExecutionDraftPort.build(any()))
        .willAnswer(
            inv -> {
              momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionRequest req =
                  inv.getArgument(0);
              return new QnaExecutionDraft(
                  req.resourceType(),
                  req.resourceId(),
                  QnaExecutionResourceStatus.PENDING_EXECUTION,
                  req.actionType(),
                  req.requesterUserId(),
                  req.counterpartyUserId(),
                  QnaEscrowIdempotencyKeyFactory.create(
                      req.actionType(), req.requesterUserId(), req.postId(), req.answerId()),
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
            baseUrl() + "/users/me/web3/execution-intents/" + intentPublicId,
            HttpMethod.GET,
            new HttpEntity<>(bearerJsonHeaders(accessToken)),
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
            baseUrl() + "/users/me/web3/execution-intents/some-id", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @Order(4)
  @DisplayName("GET /posts/{postId} — PENDING 질문은 작성자만 web3Execution summary 를 조회한다")
  void getQuestionDetail_pendingQuestion_ownerOnlyReturnsQuestionWeb3ExecutionSummary()
      throws Exception {
    ResponseEntity<String> createResponse = createQuestionPost("작성자 조회 질문", "작성자 조회 본문", 40L);
    assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    Long postId =
        objectMapper.readTree(createResponse.getBody()).path("data").path("postId").asLong();

    ResponseEntity<String> anonymousResponse =
        restTemplate.exchange(baseUrl() + "/posts/" + postId, HttpMethod.GET, null, String.class);

    assertThat(anonymousResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    ResponseEntity<String> ownerResponse =
        restTemplate.exchange(
            baseUrl() + "/posts/" + postId,
            HttpMethod.GET,
            new HttpEntity<>(bearerJsonHeaders(accessToken)),
            String.class);

    assertThat(ownerResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode data = objectMapper.readTree(ownerResponse.getBody()).path("data");
    assertThat(data.path("type").asText()).isEqualTo("QUESTION");
    assertThat(data.path("publicationStatus").asText()).isEqualTo("PENDING");
    assertThat(data.path("moderationStatus").asText()).isEqualTo("NORMAL");
    assertThat(data.path("question").path("reward").asLong()).isEqualTo(40L);
    assertThat(data.path("question").path("web3Execution").path("actionType").asText())
        .isEqualTo("QNA_QUESTION_CREATE");
    assertThat(
            data.path("question")
                .path("web3Execution")
                .path("executionIntent")
                .path("status")
                .asText())
        .isEqualTo("AWAITING_SIGNATURE");
  }

  @Test
  @Order(5)
  @DisplayName("GET /questions/{postId}/answers — owner row 에만 answer web3Execution summary 가 포함된다")
  void getAnswers_ownerOnly_returnsAnswerWeb3ExecutionSummary() throws Exception {
    TestUser questionOwner = signupAndLogin("qna-question-owner");
    TestUser answerOwner = signupAndLogin("qna-answer-owner");
    TestUser thirdUser = signupAndLogin("qna-third-user");
    Long postId =
        insertOnchainReadyQuestion(questionOwner.userId(), "답변 resume 질문", "답변 resume 본문", 25L);

    ResponseEntity<String> createAnswerResponse =
        restTemplate.exchange(
            baseUrl() + "/questions/" + postId + "/answers",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("content", "owner only resume answer", "imageIds", List.of()),
                bearerJsonHeaders(answerOwner.accessToken())),
            String.class);
    assertThat(createAnswerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    ResponseEntity<String> ownerReadResponse =
        restTemplate.exchange(
            baseUrl() + "/questions/" + postId + "/answers",
            HttpMethod.GET,
            new HttpEntity<>(bearerJsonHeaders(answerOwner.accessToken())),
            String.class);

    assertThat(ownerReadResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode ownerAnswer = objectMapper.readTree(ownerReadResponse.getBody()).path("data").get(0);
    assertThat(ownerAnswer.path("web3Execution").path("actionType").asText())
        .isEqualTo("QNA_ANSWER_SUBMIT");
    assertThat(ownerAnswer.path("web3Execution").path("executionIntent").path("status").asText())
        .isEqualTo("AWAITING_SIGNATURE");

    ResponseEntity<String> nonOwnerReadResponse =
        restTemplate.exchange(
            baseUrl() + "/questions/" + postId + "/answers",
            HttpMethod.GET,
            new HttpEntity<>(bearerJsonHeaders(thirdUser.accessToken())),
            String.class);

    assertThat(nonOwnerReadResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode nonOwnerAnswer =
        objectMapper.readTree(nonOwnerReadResponse.getBody()).path("data").get(0);
    assertThat(nonOwnerAnswer.path("web3Execution").isNull()).isTrue();
  }

  @Test
  @Order(6)
  @DisplayName("PATCH /posts/{postId} — PENDING 질문은 POST_008로 차단된다")
  void updatePendingQuestion_returnsPost008WithoutChangingContent() throws Exception {
    Long postId = createQuestionPostId("PENDING 수정 차단 질문", "원본 PENDING 본문", 30L);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/posts/" + postId,
            HttpMethod.PATCH,
            new HttpEntity<>(
                Map.of("content", "수정되면 안 되는 PENDING 본문"), bearerJsonHeaders(accessToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.path("status").asText()).isEqualTo("FAIL");
    assertThat(body.path("code").asText()).isEqualTo("POST_008");
    assertThat(getPostContent(postId)).isEqualTo("원본 PENDING 본문");
    assertThat(getPostPublicationStatus(postId)).isEqualTo("PENDING");
  }

  @Test
  @Order(7)
  @DisplayName("DELETE /posts/{postId} — PENDING 질문은 POST_008로 차단된다")
  void deletePendingQuestion_returnsPost008AndKeepsLocalPost() throws Exception {
    Long postId = createQuestionPostId("PENDING 삭제 차단 질문", "원본 삭제 차단 본문", 30L);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/posts/" + postId,
            HttpMethod.DELETE,
            new HttpEntity<>(bearerJsonHeaders(accessToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.path("status").asText()).isEqualTo("FAIL");
    assertThat(body.path("code").asText()).isEqualTo("POST_008");
    assertThat(postExists(postId)).isTrue();
    assertThat(getPostPublicationStatus(postId)).isEqualTo("PENDING");
  }

  @Test
  @Order(8)
  @DisplayName(
      "POST /posts/{postId}/web3/recover-create — terminal create intent가 있으면 edit 후 PENDING 복구")
  void recoverFailedQuestion_withTerminalCreateIntent_appliesEditAndCreatesNewIntent()
      throws Exception {
    Long postId = createQuestionPostId("복구 전 질문", "복구 전 본문", 35L);
    markQuestionPublicationStatus(postId, "FAILED");
    expireQuestionCreateIntent(postId);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/posts/" + postId + "/web3/recover-create",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("title", "복구 후 질문", "content", "복구 후 본문"), bearerJsonHeaders(accessToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode data = objectMapper.readTree(response.getBody()).path("data");
    assertThat(data.path("postId").asLong()).isEqualTo(postId);
    assertThat(data.path("web3").path("actionType").asText()).isEqualTo("QNA_QUESTION_CREATE");
    String recoveredExecutionIntentId =
        data.path("web3").path("executionIntent").path("id").asText();
    assertThat(data.path("web3").path("executionIntent").path("status").asText())
        .isEqualTo("AWAITING_SIGNATURE");
    assertThat(getPostContent(postId)).isEqualTo("복구 후 본문");
    assertThat(getPostPublicationStatus(postId)).isEqualTo("PENDING");
    assertThat(getPostCurrentCreateExecutionIntentId(postId)).isEqualTo(recoveredExecutionIntentId);
    assertThat(getPostPublicationFailureTerminalStatus(postId)).isNull();
    assertThat(getPostPublicationFailureReason(postId)).isNull();
    assertThat(getLatestQuestionCreateIntentStatus(postId)).isEqualTo("AWAITING_SIGNATURE");
    assertThat(countQuestionCreateIntents(postId)).isEqualTo(2);
  }

  @Test
  @Order(9)
  @DisplayName("POST /posts/{postId}/web3/recover-create — active create intent가 있으면 POST_008")
  void recoverFailedQuestion_withActiveCreateIntent_returnsPost008() throws Exception {
    Long postId = createQuestionPostId("active 복구 차단 질문", "active 원본 본문", 35L);
    markQuestionPublicationStatus(postId, "FAILED");

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/posts/" + postId + "/web3/recover-create",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("content", "active 상태에서 바뀌면 안 됨"), bearerJsonHeaders(accessToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.path("status").asText()).isEqualTo("FAIL");
    assertThat(body.path("code").asText()).isEqualTo("POST_008");
    assertThat(getPostContent(postId)).isEqualTo("active 원본 본문");
    assertThat(getPostPublicationStatus(postId)).isEqualTo("FAILED");
    assertThat(countQuestionCreateIntents(postId)).isEqualTo(1);
  }

  @Test
  @Order(10)
  @DisplayName("POST /posts/{postId}/web3/recover-create — projection이 있으면 POST_010이 우선한다")
  void recoverFailedQuestion_withProjectionAndActiveIntent_returnsPost010() throws Exception {
    Long postId = createQuestionPostId("projection 복구 차단 질문", "projection 원본 본문", 35L);
    markQuestionPublicationStatus(postId, "FAILED");
    insertQuestionProjection(postId, currentUserId, "projection 원본 본문", 35L);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/posts/" + postId + "/web3/recover-create",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("content", "projection 상태에서 바뀌면 안 됨"), bearerJsonHeaders(accessToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.path("status").asText()).isEqualTo("FAIL");
    assertThat(body.path("code").asText()).isEqualTo("POST_010");
    assertThat(getPostContent(postId)).isEqualTo("projection 원본 본문");
    assertThat(getPostPublicationStatus(postId)).isEqualTo("FAILED");
    assertThat(countQuestionCreateIntents(postId)).isEqualTo(1);
  }

  @Test
  @Order(11)
  @DisplayName("POST /posts/{postId}/web3/recover-create — terminal create intent가 없으면 POST_011")
  void recoverFailedQuestion_withoutTerminalCreateIntent_returnsPost011() throws Exception {
    Long postId = insertFailedQuestionPost(currentUserId, "terminal 없음 질문", "terminal 없음 본문", 35L);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/posts/" + postId + "/web3/recover-create",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("content", "수정되면 안 됨"), bearerJsonHeaders(accessToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.path("status").asText()).isEqualTo("FAIL");
    assertThat(body.path("code").asText()).isEqualTo("POST_011");
    assertThat(getPostContent(postId)).isEqualTo("terminal 없음 본문");
    assertThat(getPostPublicationStatus(postId)).isEqualTo("FAILED");
    assertThat(countQuestionCreateIntents(postId)).isZero();
  }

  @Test
  @Order(12)
  @DisplayName("DELETE /posts/{postId} — FAILED + terminal create intent는 로컬 cleanup을 허용한다")
  void deleteFailedQuestion_withTerminalCreateIntent_deletesLocalPost() throws Exception {
    Long postId = createQuestionPostId("terminal 삭제 질문", "terminal 삭제 본문", 35L);
    markQuestionPublicationStatus(postId, "FAILED");
    expireQuestionCreateIntent(postId);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/posts/" + postId,
            HttpMethod.DELETE,
            new HttpEntity<>(bearerJsonHeaders(accessToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(objectMapper.readTree(response.getBody()).path("status").asText())
        .isEqualTo("SUCCESS");
    assertThat(postExists(postId)).isFalse();
  }

  @Test
  @Order(13)
  @DisplayName("DELETE /posts/{postId} — FAILED + active create intent는 POST_008로 차단된다")
  void deleteFailedQuestion_withActiveCreateIntent_returnsPost008() throws Exception {
    Long postId = createQuestionPostId("active 삭제 차단 질문", "active 삭제 차단 본문", 35L);
    markQuestionPublicationStatus(postId, "FAILED");

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/posts/" + postId,
            HttpMethod.DELETE,
            new HttpEntity<>(bearerJsonHeaders(accessToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.path("status").asText()).isEqualTo("FAIL");
    assertThat(body.path("code").asText()).isEqualTo("POST_008");
    assertThat(postExists(postId)).isTrue();
    assertThat(getPostPublicationStatus(postId)).isEqualTo("FAILED");
  }

  @Test
  @Order(14)
  @DisplayName("DELETE /posts/{postId} — FAILED + projection은 POST_010으로 차단된다")
  void deleteFailedQuestion_withProjectionAndActiveIntent_returnsPost010() throws Exception {
    Long postId = createQuestionPostId("projection 삭제 차단 질문", "projection 삭제 차단 본문", 35L);
    markQuestionPublicationStatus(postId, "FAILED");
    insertQuestionProjection(postId, currentUserId, "projection 삭제 차단 본문", 35L);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/posts/" + postId,
            HttpMethod.DELETE,
            new HttpEntity<>(bearerJsonHeaders(accessToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.path("status").asText()).isEqualTo("FAIL");
    assertThat(body.path("code").asText()).isEqualTo("POST_010");
    assertThat(postExists(postId)).isTrue();
    assertThat(getPostPublicationStatus(postId)).isEqualTo("FAILED");
  }

  private Long createQuestionPostId(String title, String content, Long reward) throws Exception {
    ResponseEntity<String> response = createQuestionPost(title, content, reward);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return objectMapper.readTree(response.getBody()).path("data").path("postId").asLong();
  }

  private void markQuestionPublicationStatus(Long postId, String publicationStatus) {
    jdbcTemplate.update(
        "UPDATE posts SET publication_status = ?, updated_at = NOW() WHERE id = ?",
        publicationStatus,
        postId);
  }

  private void expireQuestionCreateIntent(Long postId) {
    int updated =
        jdbcTemplate.update(
            "UPDATE web3_execution_intents "
                + "SET status = 'EXPIRED', last_error_code = 'AUTH_EXPIRED', "
                + "last_error_reason = 'expired for e2e', updated_at = NOW() "
                + "WHERE resource_type = 'QUESTION' "
                + "AND resource_id = ? AND action_type = 'QNA_QUESTION_CREATE'",
            postId.toString());
    assertThat(updated).isEqualTo(1);
  }

  private String getPostContent(Long postId) {
    return jdbcTemplate.queryForObject(
        "SELECT content FROM posts WHERE id = ?", String.class, postId);
  }

  private String getPostPublicationStatus(Long postId) {
    return jdbcTemplate.queryForObject(
        "SELECT publication_status FROM posts WHERE id = ?", String.class, postId);
  }

  private String getPostCurrentCreateExecutionIntentId(Long postId) {
    return jdbcTemplate.queryForObject(
        "SELECT current_create_execution_intent_id FROM posts WHERE id = ?", String.class, postId);
  }

  private String getPostPublicationFailureTerminalStatus(Long postId) {
    return jdbcTemplate.queryForObject(
        "SELECT publication_failure_terminal_status FROM posts WHERE id = ?", String.class, postId);
  }

  private String getPostPublicationFailureReason(Long postId) {
    return jdbcTemplate.queryForObject(
        "SELECT publication_failure_reason FROM posts WHERE id = ?", String.class, postId);
  }

  private boolean postExists(Long postId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM posts WHERE id = ?", Integer.class, postId);
    return count != null && count > 0;
  }

  private String getLatestQuestionCreateIntentStatus(Long postId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM web3_execution_intents "
            + "WHERE resource_type = 'QUESTION' "
            + "AND resource_id = ? AND action_type = 'QNA_QUESTION_CREATE' "
            + "ORDER BY created_at DESC, id DESC LIMIT 1",
        String.class,
        postId.toString());
  }

  private int countQuestionCreateIntents(Long postId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM web3_execution_intents "
                + "WHERE resource_type = 'QUESTION' "
                + "AND resource_id = ? AND action_type = 'QNA_QUESTION_CREATE'",
            Integer.class,
            postId.toString());
    return count == null ? 0 : count;
  }

  private Long insertFailedQuestionPost(
      Long askerUserId, String title, String content, Long rewardAmount) {
    return insertQuestionPost(askerUserId, title, content, rewardAmount, "FAILED");
  }

  private Long insertOnchainReadyQuestion(
      Long askerUserId, String title, String content, Long rewardAmount) {
    Long postId = insertQuestionPost(askerUserId, title, content, rewardAmount, "VISIBLE");
    insertQuestionProjection(postId, askerUserId, content, rewardAmount);
    return postId;
  }

  private Long insertQuestionPost(
      Long askerUserId, String title, String content, Long rewardAmount, String publicationStatus) {
    Instant now = Instant.now();
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        conn -> {
          PreparedStatement ps =
              conn.prepareStatement(
                  "INSERT INTO posts "
                      + "(user_id, type, title, content, reward, status, "
                      + "publication_status, moderation_status, created_at, updated_at) "
                      + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                  new String[] {"id"});
          ps.setLong(1, askerUserId);
          ps.setString(2, "QUESTION");
          ps.setString(3, title);
          ps.setString(4, content);
          ps.setLong(5, rewardAmount);
          ps.setString(6, "OPEN");
          ps.setString(7, publicationStatus);
          ps.setString(8, "NORMAL");
          ps.setTimestamp(9, Timestamp.from(now));
          ps.setTimestamp(10, Timestamp.from(now));
          return ps;
        },
        keyHolder);

    Number generatedKey = keyHolder.getKey();
    if (generatedKey == null) {
      throw new IllegalStateException("Failed to insert question post row");
    }

    return generatedKey.longValue();
  }

  private void insertQuestionProjection(
      Long postId, Long askerUserId, String content, Long rewardAmount) {
    Instant now = Instant.now();
    jdbcTemplate.update(
        "INSERT INTO web3_qna_questions "
            + "(post_id, question_id, asker_user_id, token_address, reward_amount_wei, "
            + "question_hash, accepted_answer_id, answer_count, state, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        postId,
        QnaEscrowIdCodec.questionId(postId),
        askerUserId,
        "0x" + "1".repeat(40),
        BigInteger.valueOf(rewardAmount),
        QnaContentHashFactory.hash(content),
        QnaEscrowIdCodec.zeroBytes32(),
        0,
        1000,
        Timestamp.from(now),
        Timestamp.from(now));
  }

  private ResponseEntity<String> createQuestionPost(String title, String content, Long reward) {
    return restTemplate.exchange(
        baseUrl() + "/posts/question",
        HttpMethod.POST,
        new HttpEntity<>(
            Map.of("title", title, "content", content, "reward", reward, "tags", List.of()),
            bearerJsonHeaders(accessToken)),
        String.class);
  }
}
