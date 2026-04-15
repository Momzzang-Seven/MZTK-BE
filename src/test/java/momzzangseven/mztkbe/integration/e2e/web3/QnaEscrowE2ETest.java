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

  /** ABI 인코딩 + 온체인 prevalidation 계층을 Mock 으로 대체 */
  @MockitoBean private BuildQnaExecutionDraftPort buildQnaExecutionDraftPort;

  /** 토큰 승인 잔액 확인 계층을 no-op Mock 으로 대체 */
  @MockitoBean private PrecheckQuestionFundingPort precheckQuestionFundingPort;

  private String accessToken;
  private Long createdPostId;

  @BeforeEach
  void setUp() {
    accessToken = signupAndLogin("QnAEscrow유저").accessToken();

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
                  "root-" + req.resourceType() + "-" + req.resourceId() + "-" + req.actionType(),
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
  @DisplayName("GET /posts/{postId} — 익명 공개 조회로 question web3Execution summary 를 반환한다")
  void getQuestionDetail_anonymous_returnsQuestionWeb3ExecutionSummary() throws Exception {
    ResponseEntity<String> createResponse = createQuestionPost("공개 조회 질문", "공개 조회 본문", 40L);
    assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    Long postId =
        objectMapper.readTree(createResponse.getBody()).path("data").path("postId").asLong();

    ResponseEntity<String> getResponse =
        restTemplate.exchange(baseUrl() + "/posts/" + postId, HttpMethod.GET, null, String.class);

    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode data = objectMapper.readTree(getResponse.getBody()).path("data");
    assertThat(data.path("type").asText()).isEqualTo("QUESTION");
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

  private Long insertOnchainReadyQuestion(
      Long askerUserId, String title, String content, Long rewardAmount) {
    Instant now = Instant.now();
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        conn -> {
          PreparedStatement ps =
              conn.prepareStatement(
                  "INSERT INTO posts (user_id, type, title, content, reward, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                  new String[] {"id"});
          ps.setLong(1, askerUserId);
          ps.setString(2, "QUESTION");
          ps.setString(3, title);
          ps.setString(4, content);
          ps.setLong(5, rewardAmount);
          ps.setString(6, "OPEN");
          ps.setTimestamp(7, Timestamp.from(now));
          ps.setTimestamp(8, Timestamp.from(now));
          return ps;
        },
        keyHolder);

    Number generatedKey = keyHolder.getKey();
    if (generatedKey == null) {
      throw new IllegalStateException("Failed to insert question post row");
    }

    Long postId = generatedKey.longValue();
    jdbcTemplate.update(
        "INSERT INTO web3_qna_questions (post_id, question_id, asker_user_id, token_address, reward_amount_wei, question_hash, accepted_answer_id, answer_count, state, created_at, updated_at) "
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
    return postId;
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
