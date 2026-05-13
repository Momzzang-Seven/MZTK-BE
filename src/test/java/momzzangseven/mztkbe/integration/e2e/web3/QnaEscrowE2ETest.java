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
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
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

  /**
   * Deterministic stub for the server-sig {@code signedAt} epoch second. Combined with the
   * application's {@code web3.escrow.sig-validity-duration} (default 900) the expected
   * client-facing {@code signatureMeta.signatureExpiresAt} is {@link #STUB_SIGNATURE_EXPIRES_AT}.
   */
  private static final long STUB_SIGNED_AT = 1_700_000_000L;

  private static final long STUB_SIGNATURE_EXPIRES_AT = STUB_SIGNED_AT + 900L;

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
              String rootIdempotencyKey =
                  req.actionType() == QnaExecutionActionType.QNA_QUESTION_UPDATE
                      ? QnaEscrowIdempotencyKeyFactory.createQuestionUpdate(
                          req.requesterUserId(),
                          req.postId(),
                          req.questionUpdateVersion(),
                          req.questionUpdateToken())
                      : QnaEscrowIdempotencyKeyFactory.create(
                          req.actionType(), req.requesterUserId(), req.postId(), req.answerId());
              return new QnaExecutionDraft(
                  req.resourceType(),
                  req.resourceId(),
                  QnaExecutionResourceStatus.PENDING_EXECUTION,
                  req.actionType(),
                  req.requesterUserId(),
                  req.counterpartyUserId(),
                  rootIdempotencyKey,
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
                  STUB_SIGNED_AT,
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

    JsonNode createWeb3 = body.path("data").path("web3");
    assertThat(createWeb3.path("signatureMeta").path("signedAt").asLong())
        .isEqualTo(STUB_SIGNED_AT);
    assertThat(createWeb3.path("signatureMeta").path("signatureExpiresAt").asLong())
        .isEqualTo(STUB_SIGNATURE_EXPIRES_AT);

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
    JsonNode answerCreateWeb3 =
        objectMapper.readTree(createAnswerResponse.getBody()).path("data").path("web3");
    assertThat(answerCreateWeb3.path("signatureMeta").path("signedAt").asLong())
        .isEqualTo(STUB_SIGNED_AT);
    assertThat(answerCreateWeb3.path("signatureMeta").path("signatureExpiresAt").asLong())
        .isEqualTo(STUB_SIGNATURE_EXPIRES_AT);

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
    JsonNode nonOwnerAnswers = objectMapper.readTree(nonOwnerReadResponse.getBody()).path("data");
    assertThat(nonOwnerAnswers.isArray()).isTrue();
    assertThat(nonOwnerAnswers.size()).isZero();
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
    assertThat(data.path("web3").path("signatureMeta").path("signedAt").asLong())
        .isEqualTo(STUB_SIGNED_AT);
    assertThat(data.path("web3").path("signatureMeta").path("signatureExpiresAt").asLong())
        .isEqualTo(STUB_SIGNATURE_EXPIRES_AT);
    assertThat(getPostContent(postId)).isEqualTo("복구 후 본문");
    assertThat(getPostPublicationStatus(postId)).isEqualTo("PENDING");
    assertThat(getPostCurrentCreateExecutionIntentId(postId)).isEqualTo(recoveredExecutionIntentId);
    assertThat(getPostPublicationFailureTerminalStatus(postId)).isNull();
    assertThat(getPostPublicationFailureReason(postId)).isNull();
    assertThat(getLatestQuestionCreateIntentStatus(postId)).isEqualTo("AWAITING_SIGNATURE");
    assertThat(countQuestionCreateIntents(postId)).isEqualTo(2);
  }

  /**
   * §MOM-393 회귀 가드 — AWAITING_SIGNATURE intent 가 그대로 살아있는 상태에서 mutation 없이 recover-create 를 호출하는
   * 시나리오 ({@code RecoverQuestionPostEscrowService#tryManagedDuplicateRecovery} 의 happy path). 이 분기가
   * {@link
   * momzzangseven.mztkbe.modules.post.application.port.out.QuestionLifecycleExecutionPort#loadQuestionCreateIntent}
   * 의 callData 비교 로직을 실제로 trigger 하는 유일한 production 경로이며, MOM-393 의 broadcast/baseline 책임 분리 버그는
   * 정확히 이 경로에서 5xx 로 노출된다.
   */
  @Test
  @Order(29)
  @DisplayName(
      "POST /posts/{postId}/web3/recover-create — AWAITING_SIGNATURE intent 가 살아있고 mutation 없으면 동일 intent reload")
  void recoverFailedQuestion_withAwaitingSignatureIntent_reloadsSameIntent() throws Exception {
    Long postId = createQuestionPostId("새로고침 재요청 질문", "새로고침 재요청 본문", 35L);
    String originalIntentId = getPostCurrentCreateExecutionIntentId(postId);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/posts/" + postId + "/web3/recover-create",
            HttpMethod.POST,
            new HttpEntity<>(Map.of(), bearerJsonHeaders(accessToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode data = objectMapper.readTree(response.getBody()).path("data");
    assertThat(data.path("postId").asLong()).isEqualTo(postId);
    assertThat(data.path("web3").path("actionType").asText()).isEqualTo("QNA_QUESTION_CREATE");
    assertThat(data.path("web3").path("executionIntent").path("id").asText())
        .isEqualTo(originalIntentId);
    assertThat(data.path("web3").path("executionIntent").path("status").asText())
        .isEqualTo("AWAITING_SIGNATURE");
    assertThat(data.path("web3").path("signatureMeta").path("signedAt").asLong())
        .isEqualTo(STUB_SIGNED_AT);
    assertThat(getPostPublicationStatus(postId)).isEqualTo("PENDING");
    assertThat(countQuestionCreateIntents(postId)).isEqualTo(1);
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

  @Test
  @Order(15)
  @DisplayName("PATCH /posts/{postId} — 같은 질문의 두 번째 content update는 새 version intent로 생성된다")
  void updateQuestionTwice_createsSupersedingVersionedIntents() throws Exception {
    Long postId = insertOnchainReadyQuestion(currentUserId, "반복 수정 질문", "원본 본문", 45L);

    ResponseEntity<String> firstResponse =
        restTemplate.exchange(
            baseUrl() + "/posts/" + postId,
            HttpMethod.PATCH,
            new HttpEntity<>(Map.of("content", "첫 번째 수정 본문"), bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(
            objectMapper
                .readTree(firstResponse.getBody())
                .path("data")
                .path("web3")
                .path("actionType")
                .asText())
        .isEqualTo("QNA_QUESTION_UPDATE");

    ResponseEntity<String> secondResponse =
        restTemplate.exchange(
            baseUrl() + "/posts/" + postId,
            HttpMethod.PATCH,
            new HttpEntity<>(Map.of("content", "두 번째 수정 본문"), bearerJsonHeaders(accessToken)),
            String.class);

    assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode secondData = objectMapper.readTree(secondResponse.getBody()).path("data");
    assertThat(secondData.path("web3").path("actionType").asText())
        .isEqualTo("QNA_QUESTION_UPDATE");
    assertThat(getPostContent(postId)).isEqualTo("두 번째 수정 본문");
    assertThat(countQuestionUpdateIntents(postId)).isEqualTo(2);
    assertThat(getQuestionUpdateStateStatus(postId, 1L)).isEqualTo("STALE");
    assertThat(getQuestionUpdateStateStatus(postId, 2L)).isEqualTo("INTENT_BOUND");
    assertThat(getLatestQuestionUpdateRoot(postId)).contains(":v2:");
  }

  @Test
  @Order(28)
  @DisplayName(
      "[E-601] PUT /questions/{postId}/answers/{answerId} — content 없는 image-only 수정은 web3 가 null (signatureMeta 도 자연히 부재)")
  void updateAnswer_imageOnly_responseHasNullWeb3AndOmitsSignatureMeta() throws Exception {
    TestUser questionOwner = signupAndLogin("e-601-q-owner");
    TestUser answerOwner = signupAndLogin("e-601-a-owner");
    Long postId = insertOnchainReadyQuestion(questionOwner.userId(), "E-601 질문", "E-601 본문", 25L);
    Long answerId = insertOnchainReadyAnswer(postId, answerOwner.userId(), "E-601 답변");

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/questions/" + postId + "/answers/" + answerId,
            HttpMethod.PUT,
            new HttpEntity<>(
                Map.of("imageIds", List.of()), bearerJsonHeaders(answerOwner.accessToken())),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode data = objectMapper.readTree(response.getBody()).path("data");
    JsonNode web3 = data.path("web3");
    assertThat(web3.isNull() || web3.isMissingNode()).isTrue();
    assertThat(countAnswerUpdateIntents(answerId)).isZero();
  }

  @Test
  @Order(26)
  @DisplayName(
      "[E-501] 7 액션 응답의 (signatureExpiresAt - signedAt) 는 일관되게 sigValidityDuration (기본 900) 과 일치한다")
  void allServerSigActions_signatureExpiresAtMinusSignedAt_equalsSigValidityDuration()
      throws Exception {
    long expectedDuration = STUB_SIGNATURE_EXPIRES_AT - STUB_SIGNED_AT;
    assertThat(expectedDuration).isEqualTo(900L);

    TestUser questionOwner = signupAndLogin("e-501-q-owner");
    TestUser answerOwner = signupAndLogin("e-501-a-owner");

    ResponseEntity<String> createQuestionResponse =
        restTemplate.exchange(
            baseUrl() + "/posts/question",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of(
                    "title", "E-501 질문", "content", "E-501 본문", "reward", 30L, "tags", List.of()),
                bearerJsonHeaders(questionOwner.accessToken())),
            String.class);
    assertThat(createQuestionResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertSignatureMetaDuration(
        objectMapper.readTree(createQuestionResponse.getBody()).path("data").path("web3"),
        expectedDuration);

    Long onchainPostId =
        insertOnchainReadyQuestion(questionOwner.userId(), "E-501 update 질문", "원본", 40L);
    ResponseEntity<String> updateQuestionResponse =
        restTemplate.exchange(
            baseUrl() + "/posts/" + onchainPostId,
            HttpMethod.PATCH,
            new HttpEntity<>(
                Map.of("content", "E-501 update 본문"),
                bearerJsonHeaders(questionOwner.accessToken())),
            String.class);
    assertThat(updateQuestionResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertSignatureMetaDuration(
        objectMapper.readTree(updateQuestionResponse.getBody()).path("data").path("web3"),
        expectedDuration);

    Long answerId = insertOnchainReadyAnswer(onchainPostId, answerOwner.userId(), "E-501 답변");
    markQuestionStateAnswered(onchainPostId);
    expireQuestionUpdateIntent(onchainPostId);
    syncQuestionProjectionHash(onchainPostId, "E-501 update 본문");
    ResponseEntity<String> acceptResponse =
        restTemplate.exchange(
            baseUrl() + "/posts/" + onchainPostId + "/answers/" + answerId + "/accept",
            HttpMethod.POST,
            new HttpEntity<>(bearerJsonHeaders(questionOwner.accessToken())),
            String.class);
    assertThat(acceptResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertSignatureMetaDuration(
        objectMapper.readTree(acceptResponse.getBody()).path("data").path("web3"),
        expectedDuration);
  }

  @Test
  @Order(27)
  @DisplayName(
      "[E-701] BuildQnaExecutionDraftPort 가 RuntimeException 던지면 question intent 행이 남지 않는다")
  void buildExecutionDraftPort_throws_questionTransactionRollsBack() throws Exception {
    BDDMockito.willThrow(new IllegalStateException("E-701 simulated KMS failure"))
        .given(buildQnaExecutionDraftPort)
        .build(any());

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/posts/question",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of(
                    "title", "E-701 질문", "content", "E-701 본문", "reward", 30L, "tags", List.of()),
                bearerJsonHeaders(accessToken)),
            String.class);

    assertThat(response.getStatusCode().is5xxServerError()).isTrue();

    Integer postsCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM posts WHERE user_id = ? AND title = ?",
            Integer.class,
            currentUserId,
            "E-701 질문");
    assertThat(postsCount).isZero();

    Integer intentsCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM web3_execution_intents "
                + "WHERE resource_type = 'QUESTION' AND action_type = 'QNA_QUESTION_CREATE'",
            Integer.class);
    assertThat(intentsCount).isZero();
  }

  @Test
  @Order(23)
  @DisplayName(
      "[E-401] GET /users/me/web3/execution-intents/{id} — 응답에 signatureMeta 키가 없다 (DTO 계약)")
  void getExecutionIntent_responseOmitsSignatureMeta() throws Exception {
    ResponseEntity<String> createResponse = createQuestionPost("E-401 질문", "E-401 본문", 30L);
    assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    Long postId =
        objectMapper.readTree(createResponse.getBody()).path("data").path("postId").asLong();
    String intentPublicId =
        jdbcTemplate.queryForObject(
            "SELECT public_id FROM web3_execution_intents WHERE resource_id = ?",
            String.class,
            postId.toString());

    ResponseEntity<String> getResponse =
        restTemplate.exchange(
            baseUrl() + "/users/me/web3/execution-intents/" + intentPublicId,
            HttpMethod.GET,
            new HttpEntity<>(bearerJsonHeaders(accessToken)),
            String.class);

    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode data = objectMapper.readTree(getResponse.getBody()).path("data");
    assertThat(data.path("resource").isMissingNode()).isFalse();
    assertThat(data.path("executionIntent").isMissingNode()).isFalse();
    assertThat(data.path("execution").isMissingNode()).isFalse();
    assertThat(data.path("signatureMeta").isMissingNode()).isTrue();
  }

  @Test
  @Order(24)
  @DisplayName(
      "[E-402] GET /posts/{postId} — 작성자 view 의 question.web3Execution summary 에 signatureMeta 키가 없다")
  void getQuestionDetail_ownerView_questionWeb3ExecutionOmitsSignatureMeta() throws Exception {
    Long postId = createQuestionPostId("E-402 질문", "E-402 본문", 30L);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/posts/" + postId,
            HttpMethod.GET,
            new HttpEntity<>(bearerJsonHeaders(accessToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode data = objectMapper.readTree(response.getBody()).path("data");
    assertThat(data.path("question").path("web3Execution").path("actionType").asText())
        .isEqualTo("QNA_QUESTION_CREATE");
    assertThat(data.path("question").path("web3Execution").path("signatureMeta").isMissingNode())
        .isTrue();
  }

  @Test
  @Order(25)
  @DisplayName(
      "[E-403] GET /questions/{postId}/answers — 작성자 view 의 answer.web3Execution summary 에 signatureMeta 키가 없다")
  void getAnswers_ownerView_answerWeb3ExecutionOmitsSignatureMeta() throws Exception {
    TestUser questionOwner = signupAndLogin("e-403-q-owner");
    TestUser answerOwner = signupAndLogin("e-403-a-owner");
    Long postId = insertOnchainReadyQuestion(questionOwner.userId(), "E-403 질문", "E-403 본문", 30L);

    ResponseEntity<String> createAnswerResponse =
        restTemplate.exchange(
            baseUrl() + "/questions/" + postId + "/answers",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("content", "E-403 답변 본문", "imageIds", List.of()),
                bearerJsonHeaders(answerOwner.accessToken())),
            String.class);
    assertThat(createAnswerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    ResponseEntity<String> readResponse =
        restTemplate.exchange(
            baseUrl() + "/questions/" + postId + "/answers",
            HttpMethod.GET,
            new HttpEntity<>(bearerJsonHeaders(answerOwner.accessToken())),
            String.class);

    assertThat(readResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode answers = objectMapper.readTree(readResponse.getBody()).path("data");
    assertThat(answers.isArray()).isTrue();
    assertThat(answers.size()).isPositive();
    JsonNode firstAnswer = answers.get(0);
    assertThat(firstAnswer.path("web3Execution").path("actionType").asText())
        .isEqualTo("QNA_ANSWER_SUBMIT");
    assertThat(firstAnswer.path("web3Execution").path("signatureMeta").isMissingNode()).isTrue();
  }

  @Test
  @Order(20)
  @DisplayName(
      "[E-201] PUT /questions/{postId}/answers/{answerId} — VISIBLE 답변 수정 응답에 signatureMeta 가 노출된다")
  void updateOnchainReadyAnswer_responseExposesAnswerUpdateSignatureMeta() throws Exception {
    TestUser questionOwner = signupAndLogin("e-201-q-owner");
    TestUser answerOwner = signupAndLogin("e-201-a-owner");
    Long postId = insertOnchainReadyQuestion(questionOwner.userId(), "E-201 질문", "E-201 본문", 30L);
    Long answerId = insertOnchainReadyAnswer(postId, answerOwner.userId(), "E-201 원본 답변");

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/questions/" + postId + "/answers/" + answerId,
            HttpMethod.PUT,
            new HttpEntity<>(
                Map.of("content", "E-201 수정된 답변", "imageIds", List.of()),
                bearerJsonHeaders(answerOwner.accessToken())),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.path("status").asText()).isEqualTo("SUCCESS");

    JsonNode web3 = body.path("data").path("web3");
    assertThat(web3.path("actionType").asText()).isEqualTo("QNA_ANSWER_UPDATE");
    assertThat(web3.path("signatureMeta").path("signedAt").asLong()).isEqualTo(STUB_SIGNED_AT);
    assertThat(web3.path("signatureMeta").path("signatureExpiresAt").asLong())
        .isEqualTo(STUB_SIGNATURE_EXPIRES_AT);

    assertThat(countAnswerUpdateIntents(answerId)).isEqualTo(1);
  }

  @Test
  @Order(21)
  @DisplayName(
      "[E-202] DELETE /questions/{postId}/answers/{answerId} — VISIBLE 답변 삭제 응답에 signatureMeta 가 노출된다")
  void deleteOnchainReadyAnswer_responseExposesAnswerDeleteSignatureMeta() throws Exception {
    TestUser questionOwner = signupAndLogin("e-202-q-owner");
    TestUser answerOwner = signupAndLogin("e-202-a-owner");
    Long postId = insertOnchainReadyQuestion(questionOwner.userId(), "E-202 질문", "E-202 본문", 25L);
    Long answerId = insertOnchainReadyAnswer(postId, answerOwner.userId(), "E-202 답변 본문");

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/questions/" + postId + "/answers/" + answerId,
            HttpMethod.DELETE,
            new HttpEntity<>(bearerJsonHeaders(answerOwner.accessToken())),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.path("status").asText()).isEqualTo("SUCCESS");

    JsonNode web3 = body.path("data").path("web3");
    assertThat(web3.path("actionType").asText()).isEqualTo("QNA_ANSWER_DELETE");
    assertThat(web3.path("signatureMeta").path("signedAt").asLong()).isEqualTo(STUB_SIGNED_AT);
    assertThat(web3.path("signatureMeta").path("signatureExpiresAt").asLong())
        .isEqualTo(STUB_SIGNATURE_EXPIRES_AT);

    assertThat(countAnswerDeleteIntents(answerId)).isEqualTo(1);
  }

  @Test
  @Order(22)
  @DisplayName(
      "[E-203] POST /posts/{postId}/answers/{answerId}/accept — ACCEPT 응답에 signatureMeta 가 노출된다")
  void acceptOnchainReadyAnswer_responseExposesAnswerAcceptSignatureMeta() throws Exception {
    TestUser questionOwner = signupAndLogin("e-203-q-owner");
    TestUser answerOwner = signupAndLogin("e-203-a-owner");
    Long postId = insertOnchainReadyQuestion(questionOwner.userId(), "E-203 질문", "E-203 본문", 40L);
    Long answerId = insertOnchainReadyAnswer(postId, answerOwner.userId(), "E-203 답변 본문");
    markQuestionStateAnswered(postId);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/posts/" + postId + "/answers/" + answerId + "/accept",
            HttpMethod.POST,
            new HttpEntity<>(bearerJsonHeaders(questionOwner.accessToken())),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.path("status").asText()).isEqualTo("SUCCESS");

    JsonNode data = body.path("data");
    assertThat(data.path("acceptedAnswerId").asLong()).isEqualTo(answerId);
    JsonNode web3 = data.path("web3");
    assertThat(web3.path("actionType").asText()).isEqualTo("QNA_ANSWER_ACCEPT");
    assertThat(web3.path("signatureMeta").path("signedAt").asLong()).isEqualTo(STUB_SIGNED_AT);
    assertThat(web3.path("signatureMeta").path("signatureExpiresAt").asLong())
        .isEqualTo(STUB_SIGNATURE_EXPIRES_AT);

    assertThat(countAnswerAcceptIntents(postId, answerId)).isEqualTo(1);
  }

  @Test
  @Order(17)
  @DisplayName("[E-101] PATCH /posts/{postId} — VISIBLE 질문 수정 응답에 signatureMeta 가 노출된다")
  void updateOnchainReadyQuestion_responseExposesSignatureMeta() throws Exception {
    Long postId = insertOnchainReadyQuestion(currentUserId, "E-101 질문", "E-101 원본 본문", 35L);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/posts/" + postId,
            HttpMethod.PATCH,
            new HttpEntity<>(Map.of("content", "E-101 수정 본문"), bearerJsonHeaders(accessToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.path("status").asText()).isEqualTo("SUCCESS");

    JsonNode web3 = body.path("data").path("web3");
    assertThat(web3.path("actionType").asText()).isEqualTo("QNA_QUESTION_UPDATE");
    assertThat(web3.path("signatureMeta").path("signedAt").asLong()).isEqualTo(STUB_SIGNED_AT);
    assertThat(web3.path("signatureMeta").path("signatureExpiresAt").asLong())
        .isEqualTo(STUB_SIGNATURE_EXPIRES_AT);

    assertThat(getPostContent(postId)).isEqualTo("E-101 수정 본문");
    assertThat(countQuestionUpdateIntents(postId)).isEqualTo(1);
  }

  @Test
  @Order(18)
  @DisplayName(
      "[E-102] DELETE /posts/{postId} — VISIBLE 질문 삭제 응답에 QNA_QUESTION_DELETE signatureMeta 가 노출된다")
  void deleteOnchainReadyQuestion_responseExposesQuestionDeleteSignatureMeta() throws Exception {
    Long postId = insertOnchainReadyQuestion(currentUserId, "E-102 질문", "E-102 원본 본문", 25L);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/posts/" + postId,
            HttpMethod.DELETE,
            new HttpEntity<>(bearerJsonHeaders(accessToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.path("status").asText()).isEqualTo("SUCCESS");

    JsonNode web3 = body.path("data").path("web3");
    assertThat(web3.path("actionType").asText()).isEqualTo("QNA_QUESTION_DELETE");
    assertThat(web3.path("signatureMeta").path("signedAt").asLong()).isEqualTo(STUB_SIGNED_AT);
    assertThat(web3.path("signatureMeta").path("signatureExpiresAt").asLong())
        .isEqualTo(STUB_SIGNATURE_EXPIRES_AT);

    assertThat(countQuestionDeleteIntents(postId)).isEqualTo(1);
    assertThat(postExists(postId)).isTrue();
  }

  @Test
  @Order(19)
  @DisplayName("[E-104] PATCH 두 번 — 두 UPDATE 응답 모두 signatureMeta 를 노출한다")
  void updateOnchainReadyQuestionTwice_bothResponsesExposeSignatureMeta() throws Exception {
    Long postId = insertOnchainReadyQuestion(currentUserId, "E-104 질문", "E-104 원본 본문", 45L);

    ResponseEntity<String> firstResponse =
        restTemplate.exchange(
            baseUrl() + "/posts/" + postId,
            HttpMethod.PATCH,
            new HttpEntity<>(Map.of("content", "E-104 첫 번째 수정 본문"), bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode firstWeb3 = objectMapper.readTree(firstResponse.getBody()).path("data").path("web3");
    assertThat(firstWeb3.path("actionType").asText()).isEqualTo("QNA_QUESTION_UPDATE");
    assertThat(firstWeb3.path("signatureMeta").path("signedAt").asLong()).isEqualTo(STUB_SIGNED_AT);
    assertThat(firstWeb3.path("signatureMeta").path("signatureExpiresAt").asLong())
        .isEqualTo(STUB_SIGNATURE_EXPIRES_AT);

    ResponseEntity<String> secondResponse =
        restTemplate.exchange(
            baseUrl() + "/posts/" + postId,
            HttpMethod.PATCH,
            new HttpEntity<>(Map.of("content", "E-104 두 번째 수정 본문"), bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode secondWeb3 = objectMapper.readTree(secondResponse.getBody()).path("data").path("web3");
    assertThat(secondWeb3.path("actionType").asText()).isEqualTo("QNA_QUESTION_UPDATE");
    assertThat(secondWeb3.path("signatureMeta").path("signedAt").asLong())
        .isEqualTo(STUB_SIGNED_AT);
    assertThat(secondWeb3.path("signatureMeta").path("signatureExpiresAt").asLong())
        .isEqualTo(STUB_SIGNATURE_EXPIRES_AT);

    assertThat(getPostContent(postId)).isEqualTo("E-104 두 번째 수정 본문");
    assertThat(countQuestionUpdateIntents(postId)).isEqualTo(2);
  }

  @Test
  @Order(16)
  @DisplayName(
      "POST /questions/{postId}/answers/{answerId}/web3/recover-create — 응답 web3.signatureMeta 가 노출된다")
  void recoverFailedAnswer_responseExposesSignatureMeta() throws Exception {
    TestUser questionOwner = signupAndLogin("answer-recover-q-owner");
    TestUser answerOwner = signupAndLogin("answer-recover-a-owner");
    Long postId =
        insertOnchainReadyQuestion(
            questionOwner.userId(), "answer recover 질문", "answer recover 본문", 30L);

    ResponseEntity<String> createAnswerResponse =
        restTemplate.exchange(
            baseUrl() + "/questions/" + postId + "/answers",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("content", "answer recover 답변 본문", "imageIds", List.of()),
                bearerJsonHeaders(answerOwner.accessToken())),
            String.class);
    assertThat(createAnswerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    Long answerId =
        objectMapper
            .readTree(createAnswerResponse.getBody())
            .path("data")
            .path("answerId")
            .asLong();

    markAnswerPublicationStatus(answerId, "FAILED");
    expireAnswerCreateIntent(answerId);

    ResponseEntity<String> recoverResponse =
        restTemplate.exchange(
            baseUrl() + "/questions/" + postId + "/answers/" + answerId + "/web3/recover-create",
            HttpMethod.POST,
            new HttpEntity<>(bearerJsonHeaders(answerOwner.accessToken())),
            String.class);

    assertThat(recoverResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode recoverData = objectMapper.readTree(recoverResponse.getBody()).path("data");
    assertThat(recoverData.path("answerId").asLong()).isEqualTo(answerId);
    assertThat(recoverData.path("web3").path("actionType").asText()).isEqualTo("QNA_ANSWER_SUBMIT");
    assertThat(recoverData.path("web3").path("signatureMeta").path("signedAt").asLong())
        .isEqualTo(STUB_SIGNED_AT);
    assertThat(recoverData.path("web3").path("signatureMeta").path("signatureExpiresAt").asLong())
        .isEqualTo(STUB_SIGNATURE_EXPIRES_AT);
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

  private void markAnswerPublicationStatus(Long answerId, String publicationStatus) {
    int updated =
        jdbcTemplate.update(
            "UPDATE answers SET publication_status = ?, updated_at = NOW() WHERE id = ?",
            publicationStatus,
            answerId);
    assertThat(updated).isEqualTo(1);
  }

  private void expireAnswerCreateIntent(Long answerId) {
    int updated =
        jdbcTemplate.update(
            "UPDATE web3_execution_intents "
                + "SET status = 'EXPIRED', last_error_code = 'AUTH_EXPIRED', "
                + "last_error_reason = 'expired for e2e', updated_at = NOW() "
                + "WHERE resource_type = 'ANSWER' "
                + "AND resource_id = ? AND action_type = 'QNA_ANSWER_SUBMIT'",
            answerId.toString());
    assertThat(updated).isEqualTo(1);
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

  private void expireQuestionUpdateIntent(Long postId) {
    int updated =
        jdbcTemplate.update(
            "UPDATE web3_execution_intents "
                + "SET status = 'EXPIRED', last_error_code = 'AUTH_EXPIRED', "
                + "last_error_reason = 'expired for e2e', updated_at = NOW() "
                + "WHERE resource_type = 'QUESTION' "
                + "AND resource_id = ? AND action_type = 'QNA_QUESTION_UPDATE'",
            postId.toString());
    assertThat(updated).isPositive();
  }

  private void syncQuestionProjectionHash(Long postId, String content) {
    int updated =
        jdbcTemplate.update(
            "UPDATE web3_qna_questions SET question_hash = ?, updated_at = NOW() "
                + "WHERE post_id = ?",
            QnaContentHashFactory.hash(content),
            postId);
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

  private int countQuestionUpdateIntents(Long postId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM web3_execution_intents "
                + "WHERE resource_type = 'QUESTION' "
                + "AND resource_id = ? AND action_type = 'QNA_QUESTION_UPDATE'",
            Integer.class,
            postId.toString());
    return count == null ? 0 : count;
  }

  private int countQuestionDeleteIntents(Long postId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM web3_execution_intents "
                + "WHERE resource_type = 'QUESTION' "
                + "AND resource_id = ? AND action_type = 'QNA_QUESTION_DELETE'",
            Integer.class,
            postId.toString());
    return count == null ? 0 : count;
  }

  private int countAnswerUpdateIntents(Long answerId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM web3_execution_intents "
                + "WHERE resource_type = 'ANSWER' "
                + "AND resource_id = ? AND action_type = 'QNA_ANSWER_UPDATE'",
            Integer.class,
            answerId.toString());
    return count == null ? 0 : count;
  }

  private int countAnswerDeleteIntents(Long answerId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM web3_execution_intents "
                + "WHERE resource_type = 'ANSWER' "
                + "AND resource_id = ? AND action_type = 'QNA_ANSWER_DELETE'",
            Integer.class,
            answerId.toString());
    return count == null ? 0 : count;
  }

  private int countAnswerAcceptIntents(Long postId, Long answerId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM web3_execution_intents "
                + "WHERE resource_type = 'QUESTION' "
                + "AND resource_id = ? AND action_type = 'QNA_ANSWER_ACCEPT'",
            Integer.class,
            postId.toString());
    return count == null ? 0 : count;
  }

  private Long insertOnchainReadyAnswer(Long postId, Long answerOwnerId, String content) {
    Instant now = Instant.now();
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        conn -> {
          PreparedStatement ps =
              conn.prepareStatement(
                  "INSERT INTO answers "
                      + "(post_id, user_id, content, is_accepted, publication_status, "
                      + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                  new String[] {"id"});
          ps.setLong(1, postId);
          ps.setLong(2, answerOwnerId);
          ps.setString(3, content);
          ps.setBoolean(4, false);
          ps.setString(5, "VISIBLE");
          ps.setTimestamp(6, Timestamp.from(now));
          ps.setTimestamp(7, Timestamp.from(now));
          return ps;
        },
        keyHolder);

    Number generatedKey = keyHolder.getKey();
    if (generatedKey == null) {
      throw new IllegalStateException("Failed to insert answer row");
    }
    Long answerId = generatedKey.longValue();

    jdbcTemplate.update(
        "INSERT INTO web3_qna_answers "
            + "(answer_id, post_id, question_id, answer_key, responder_user_id, content_hash, "
            + "accepted, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        answerId,
        postId,
        QnaEscrowIdCodec.questionId(postId),
        QnaEscrowIdCodec.answerId(answerId),
        answerOwnerId,
        QnaContentHashFactory.hash(content),
        false,
        Timestamp.from(now),
        Timestamp.from(now));

    jdbcTemplate.update(
        "UPDATE web3_qna_questions SET answer_count = answer_count + 1, updated_at = NOW() "
            + "WHERE post_id = ?",
        postId);

    return answerId;
  }

  private void markQuestionStateAnswered(Long postId) {
    int updated =
        jdbcTemplate.update(
            "UPDATE web3_qna_questions SET state = ?, updated_at = NOW() WHERE post_id = ?",
            1100,
            postId);
    assertThat(updated).isEqualTo(1);
  }

  private void assertSignatureMetaDuration(JsonNode web3, long expectedDuration) {
    long signedAt = web3.path("signatureMeta").path("signedAt").asLong();
    long expiresAt = web3.path("signatureMeta").path("signatureExpiresAt").asLong();
    assertThat(signedAt).isPositive();
    assertThat(expiresAt - signedAt).isEqualTo(expectedDuration);
  }

  private String getQuestionUpdateStateStatus(Long postId, Long updateVersion) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM qna_question_update_states "
            + "WHERE post_id = ? AND update_version = ?",
        String.class,
        postId,
        updateVersion);
  }

  private String getLatestQuestionUpdateRoot(Long postId) {
    return jdbcTemplate.queryForObject(
        "SELECT root_idempotency_key FROM web3_execution_intents "
            + "WHERE resource_type = 'QUESTION' "
            + "AND resource_id = ? AND action_type = 'QNA_QUESTION_UPDATE' "
            + "ORDER BY created_at DESC, id DESC LIMIT 1",
        String.class,
        postId.toString());
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
