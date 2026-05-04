package momzzangseven.mztkbe.integration.e2e.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.MarkExecutionIntentSucceededUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.RunInternalExecutionBatchUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionEip1559SigningPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionTransactionGatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadSponsorTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.VerifyTreasuryWalletForSignPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaContentHashFactory;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdCodec;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaQuestionState;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.config.QnaAdminExecutionConfigurationValidator;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.web3.QnaContractCallSupport;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.ExecutionSignerCapabilityView;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.ProbeExecutionSignerCapabilityUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@TestPropertySource(
    properties = {
      "web3.reward-token.enabled=true",
      "web3.eip7702.enabled=false",
      "web3.execution.internal.enabled=true",
      "web3.qna.admin.enabled=true",
      "web3.execution.internal.signer.wallet-alias=test-sponsor",
      "web3.execution.internal.signer.key-encryption-key-b64=dGVzdA=="
    })
@Tag("e2e")
@DisplayName("[E2E] QnA admin manual settle/refund flow")
class QnaAdminEscrowE2ETest extends E2ETestBase {

  private static final Instant NOW = Instant.parse("2026-04-21T00:00:00Z");
  private static final String SIGNER_ADDRESS = "0x0d8e461687b7d06f86ec348e0c270b0f279855f0";
  private static final String CALL_TARGET = "0x0000000000000000000000000000000000000000";
  private static final String TOKEN_ADDRESS = "0x1111111111111111111111111111111111111111";
  private static final BigInteger REWARD_AMOUNT_WEI = new BigInteger("50000000000000000000");

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private RunInternalExecutionBatchUseCase runInternalExecutionBatchUseCase;
  @Autowired private MarkExecutionIntentSucceededUseCase markExecutionIntentSucceededUseCase;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;

  @MockitoBean
  private QnaAdminExecutionConfigurationValidator qnaAdminExecutionConfigurationValidator;

  @MockitoBean private QnaContractCallSupport qnaContractCallSupport;
  @MockitoBean private LoadSponsorTreasuryWalletPort loadSponsorTreasuryWalletPort;
  @MockitoBean private VerifyTreasuryWalletForSignPort verifyTreasuryWalletForSignPort;
  @MockitoBean private ExecutionEip1559SigningPort executionEip1559SigningPort;
  @MockitoBean private ExecutionTransactionGatewayPort executionTransactionGatewayPort;
  @MockitoBean private ProbeExecutionSignerCapabilityUseCase probeExecutionSignerCapabilityUseCase;

  @BeforeEach
  void setUp() {
    BDDMockito.given(loadSponsorTreasuryWalletPort.load())
        .willReturn(
            Optional.of(
                new TreasuryWalletInfo(
                    "test-sponsor", "alias/test-sponsor", SIGNER_ADDRESS, true)));
    BDDMockito.given(probeExecutionSignerCapabilityUseCase.execute())
        .willReturn(ExecutionSignerCapabilityView.ready("test-sponsor", SIGNER_ADDRESS));
    BDDMockito.willDoNothing().given(verifyTreasuryWalletForSignPort).verify(anyString());
    BDDMockito.given(qnaContractCallSupport.isRelayerRegistered(anyString(), anyString()))
        .willReturn(true);
    BDDMockito.willDoNothing()
        .given(qnaContractCallSupport)
        .requireRelayerCallable(anyString(), anyString());
    BDDMockito.given(
            qnaContractCallSupport.prevalidateContractCall(anyString(), anyString(), anyString()))
        .willReturn(
            new QnaContractCallSupport.QnaCallPrevalidationResult(
                BigInteger.valueOf(210_000L),
                BigInteger.valueOf(2_000_000_000L),
                BigInteger.valueOf(30_000_000_000L)));
    BDDMockito.given(executionEip1559SigningPort.sign(any()))
        .willReturn(new ExecutionEip1559SigningPort.SignedTransaction("0xsigned", "0xhash-signed"));
    BDDMockito.given(executionTransactionGatewayPort.reserveNextNonce(anyString())).willReturn(77L);
  }

  @Test
  @DisplayName(
      "admin settlement review/settle가 real draft builder와 internal issuer를 통해 admin settle 완료까지 이어진다")
  void adminSettlementFlow_executesInternalIssuerAndConfirmsAdminSettle() throws Exception {
    AtomicReference<ExecutionTransactionGatewayPort.CreateTransactionCommand> createdCommandRef =
        new AtomicReference<>();
    BDDMockito.given(executionTransactionGatewayPort.createAndFlush(any()))
        .willAnswer(
            invocation -> {
              var command =
                  invocation.getArgument(
                      0, ExecutionTransactionGatewayPort.CreateTransactionCommand.class);
              createdCommandRef.set(command);
              return seedTransactionRecord(801L, command);
            });
    BDDMockito.given(executionTransactionGatewayPort.broadcast("0xsigned"))
        .willReturn(
            new ExecutionTransactionGatewayPort.BroadcastResult(true, "0xhash801", null, "main"));

    AdminUser admin = createAdminAndLogin();
    TestUser asker = signupAndLogin("manual-settle-asker");
    TestUser responder = signupAndLogin("manual-settle-responder");
    SeededSettlementScenario scenario =
        seedSettlementScenario(
            asker.userId(), responder.userId(), "manual settle question", "manual settle answer");

    ResponseEntity<String> reviewResponse =
        restTemplate.exchange(
            baseUrl()
                + "/admin/web3/qna/questions/"
                + scenario.postId()
                + "/answers/"
                + scenario.answerId()
                + "/settlement-review",
            HttpMethod.GET,
            new HttpEntity<>(bearerJsonHeaders(admin.accessToken())),
            String.class);

    assertThat(reviewResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode reviewData = objectMapper.readTree(reviewResponse.getBody()).path("data");
    assertThat(reviewData.path("processable").asBoolean()).isTrue();
    assertThat(reviewData.path("questionConflictingActiveIntent").asBoolean()).isFalse();
    assertThat(reviewData.path("answerConflictingActiveIntent").asBoolean()).isFalse();
    assertThat(reviewData.path("authority").path("serverSigner").path("signerAddress").asText())
        .isEqualTo(SIGNER_ADDRESS);
    assertThat(reviewData.path("authority").path("relayerRegistered").asBoolean()).isTrue();
    assertThat(reviewData.path("authority").path("relayerRegistrationStatus").asText())
        .isEqualTo("REGISTERED");

    ResponseEntity<String> settleResponse =
        restTemplate.exchange(
            baseUrl()
                + "/admin/web3/qna/questions/"
                + scenario.postId()
                + "/answers/"
                + scenario.answerId()
                + "/settle",
            HttpMethod.POST,
            new HttpEntity<>(bearerJsonHeaders(admin.accessToken())),
            String.class);

    assertThat(settleResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode settleData = objectMapper.readTree(settleResponse.getBody()).path("data");
    assertThat(settleData.path("actionType").asText()).isEqualTo("QNA_ADMIN_SETTLE");
    assertThat(settleData.path("execution").path("mode").asText()).isEqualTo("EIP1559");
    assertThat(settleData.path("execution").path("requiresUserSignature").asBoolean()).isFalse();
    assertThat(settleData.path("execution").path("authorityModel").asText())
        .isEqualTo("SERVER_RELAYER_ONLY");
    assertThat(settleData.path("signRequest").isMissingNode()).isTrue();

    Map<String, Object> latestIntent = latestExecutionIntent(scenario.postId());
    String intentId = settleData.path("executionIntent").path("id").asText();
    assertThat(latestIntent.get("action_type")).isEqualTo("QNA_ADMIN_SETTLE");
    assertThat(latestIntent.get("status")).isEqualTo("AWAITING_SIGNATURE");
    assertThat(postStatus(scenario.postId())).isEqualTo("PENDING_ACCEPT");
    assertThat(countAdminAudit("QNA_ADMIN_SETTLE", "post:" + scenario.postId(), admin.userId()))
        .isEqualTo(1);
    assertThat(objectMapper.readTree(unsignedTxSnapshot(intentId)).path("expectedNonce").asLong())
        .isZero();

    var internalResult = runInternalExecutionBatchUseCase.runBatch(NOW);

    assertThat(internalResult.executedCount()).isEqualTo(1);
    assertThat(internalResult.pendingCount()).isEqualTo(1);
    assertThat(createdCommandRef.get()).isNotNull();
    assertThat(createdCommandRef.get().fromAddress()).isEqualTo(SIGNER_ADDRESS);
    assertThat(createdCommandRef.get().toAddress()).isEqualTo(CALL_TARGET);
    assertThat(createdCommandRef.get().nonce()).isEqualTo(77L);
    assertThat(transactionNonce(801L)).isEqualTo(77L);
    assertThat(latestExecutionIntentStatus(scenario.postId())).isEqualTo("PENDING_ONCHAIN");

    markExecutionIntentSucceededUseCase.execute(801L);

    assertThat(postStatus(scenario.postId())).isEqualTo("RESOLVED");
    assertThat(acceptedAnswerId(scenario.postId())).isEqualTo(scenario.answerId());
    assertThat(localAnswerAccepted(scenario.answerId())).isTrue();
    assertThat(latestExecutionIntentStatus(scenario.postId())).isEqualTo("CONFIRMED");
    assertThat(questionState(scenario.postId())).isEqualTo(QnaQuestionState.ADMIN_SETTLED.code());
    assertThat(answerProjectionAccepted(scenario.answerId())).isTrue();
  }

  @Test
  @DisplayName(
      "admin refund review/refund가 real draft builder와 internal issuer를 통해 admin refund 완료까지 이어진다")
  void adminRefundFlow_executesInternalIssuerAndConfirmsAdminRefund() throws Exception {
    AtomicReference<ExecutionTransactionGatewayPort.CreateTransactionCommand> createdCommandRef =
        new AtomicReference<>();
    BDDMockito.given(executionTransactionGatewayPort.createAndFlush(any()))
        .willAnswer(
            invocation -> {
              var command =
                  invocation.getArgument(
                      0, ExecutionTransactionGatewayPort.CreateTransactionCommand.class);
              createdCommandRef.set(command);
              return seedTransactionRecord(802L, command);
            });
    BDDMockito.given(executionTransactionGatewayPort.broadcast("0xsigned"))
        .willReturn(
            new ExecutionTransactionGatewayPort.BroadcastResult(true, "0xhash802", null, "main"));

    AdminUser admin = createAdminAndLogin();
    TestUser asker = signupAndLogin("manual-refund-asker");
    Long postId = seedRefundScenario(asker.userId(), "manual refund question");

    ResponseEntity<String> reviewResponse =
        restTemplate.exchange(
            baseUrl() + "/admin/web3/qna/questions/" + postId + "/refund-review",
            HttpMethod.GET,
            new HttpEntity<>(bearerJsonHeaders(admin.accessToken())),
            String.class);

    assertThat(reviewResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode reviewData = objectMapper.readTree(reviewResponse.getBody()).path("data");
    assertThat(reviewData.path("processable").asBoolean()).isTrue();
    assertThat(reviewData.path("questionConflictingActiveIntent").asBoolean()).isFalse();
    assertThat(reviewData.path("answerConflictingActiveIntent").asBoolean()).isFalse();
    assertThat(reviewData.path("authority").path("serverSigner").path("signerAddress").asText())
        .isEqualTo(SIGNER_ADDRESS);
    assertThat(reviewData.path("authority").path("relayerRegistered").asBoolean()).isTrue();
    assertThat(reviewData.path("authority").path("relayerRegistrationStatus").asText())
        .isEqualTo("REGISTERED");

    ResponseEntity<String> refundResponse =
        restTemplate.exchange(
            baseUrl() + "/admin/web3/qna/questions/" + postId + "/refund",
            HttpMethod.POST,
            new HttpEntity<>(bearerJsonHeaders(admin.accessToken())),
            String.class);

    assertThat(refundResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode refundData = objectMapper.readTree(refundResponse.getBody()).path("data");
    assertThat(refundData.path("actionType").asText()).isEqualTo("QNA_ADMIN_REFUND");
    assertThat(refundData.path("execution").path("mode").asText()).isEqualTo("EIP1559");
    assertThat(refundData.path("execution").path("requiresUserSignature").asBoolean()).isFalse();
    assertThat(refundData.path("execution").path("authorityModel").asText())
        .isEqualTo("SERVER_RELAYER_ONLY");
    assertThat(refundData.path("signRequest").isMissingNode()).isTrue();

    Map<String, Object> latestIntent = latestExecutionIntent(postId);
    String intentId = refundData.path("executionIntent").path("id").asText();
    assertThat(latestIntent.get("action_type")).isEqualTo("QNA_ADMIN_REFUND");
    assertThat(latestIntent.get("status")).isEqualTo("AWAITING_SIGNATURE");
    assertThat(postStatus(postId)).isEqualTo("PENDING_ADMIN_REFUND");
    assertThat(countAdminAudit("QNA_ADMIN_REFUND", "post:" + postId, admin.userId())).isEqualTo(1);
    assertThat(objectMapper.readTree(unsignedTxSnapshot(intentId)).path("expectedNonce").asLong())
        .isZero();

    var internalResult = runInternalExecutionBatchUseCase.runBatch(NOW);

    assertThat(internalResult.executedCount()).isEqualTo(1);
    assertThat(internalResult.pendingCount()).isEqualTo(1);
    assertThat(createdCommandRef.get()).isNotNull();
    assertThat(createdCommandRef.get().fromAddress()).isEqualTo(SIGNER_ADDRESS);
    assertThat(createdCommandRef.get().toAddress()).isEqualTo(CALL_TARGET);
    assertThat(createdCommandRef.get().nonce()).isEqualTo(77L);
    assertThat(transactionNonce(802L)).isEqualTo(77L);
    assertThat(latestExecutionIntentStatus(postId)).isEqualTo("PENDING_ONCHAIN");

    markExecutionIntentSucceededUseCase.execute(802L);

    assertThat(latestExecutionIntentStatus(postId)).isEqualTo("CONFIRMED");
    assertThat(questionState(postId)).isEqualTo(QnaQuestionState.DELETED.code());
    assertThat(postExists(postId)).isFalse();
  }

  private AdminUser createAdminAndLogin() {
    String email = randomEmail();
    Long userId = signupUser(email, DEFAULT_TEST_PASSWORD, "QnaAdminE2E");
    jdbcTemplate.update("UPDATE users SET role = 'ADMIN_GENERATED' WHERE id = ?", userId);

    String loginId = String.valueOf(10000000 + (int) (Math.random() * 90000000));
    jdbcTemplate.update(
        "INSERT INTO admin_accounts (user_id, login_id, password_hash, created_by,"
            + " last_login_at, password_last_rotated_at, deleted_at, created_at, updated_at)"
            + " VALUES (?, ?, ?, NULL, NULL, NULL, NULL, NOW(), NOW())",
        userId,
        loginId,
        passwordEncoder.encode(DEFAULT_TEST_PASSWORD));

    ResponseEntity<String> loginResponse =
        restTemplate.exchange(
            baseUrl() + "/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of(
                    "provider",
                    "LOCAL_ADMIN",
                    "loginId",
                    loginId,
                    "password",
                    DEFAULT_TEST_PASSWORD),
                jsonOnlyHeaders()),
            String.class);

    assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    try {
      String accessToken =
          objectMapper.readTree(loginResponse.getBody()).at("/data/accessToken").asText();
      return new AdminUser(userId, accessToken);
    } catch (Exception e) {
      throw new IllegalStateException(
          "failed to parse admin login response: " + loginResponse.getBody(), e);
    }
  }

  private SeededSettlementScenario seedSettlementScenario(
      Long askerUserId, Long responderUserId, String questionContent, String answerContent) {
    LocalDateTime createdAt = LocalDateTime.now().minusHours(2);
    Long postId = insertQuestionPost(askerUserId, "admin settle title", questionContent, createdAt);
    Long answerId = insertAnswer(postId, responderUserId, answerContent, createdAt.plusMinutes(5));
    insertQuestionProjection(
        postId, askerUserId, questionContent, 1, QnaQuestionState.ANSWERED, createdAt);
    insertAnswerProjection(
        postId, answerId, responderUserId, answerContent, createdAt.plusMinutes(5));
    return new SeededSettlementScenario(postId, answerId);
  }

  private Long seedRefundScenario(Long askerUserId, String questionContent) {
    LocalDateTime createdAt = LocalDateTime.now().minusHours(1);
    Long postId = insertQuestionPost(askerUserId, "admin refund title", questionContent, createdAt);
    insertQuestionProjection(
        postId, askerUserId, questionContent, 0, QnaQuestionState.CREATED, createdAt);
    return postId;
  }

  private Long insertQuestionPost(
      Long userId, String title, String content, LocalDateTime createdAt) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        conn -> {
          PreparedStatement ps =
              conn.prepareStatement(
                  "insert into posts (user_id, type, title, content, reward, status, created_at, updated_at)"
                      + " values (?, ?, ?, ?, ?, ?, ?, ?)",
                  new String[] {"id"});
          ps.setLong(1, userId);
          ps.setString(2, "QUESTION");
          ps.setString(3, title);
          ps.setString(4, content);
          ps.setLong(5, 50L);
          ps.setString(6, "OPEN");
          ps.setTimestamp(7, Timestamp.valueOf(createdAt));
          ps.setTimestamp(8, Timestamp.valueOf(createdAt));
          return ps;
        },
        keyHolder);
    return keyHolder.getKey().longValue();
  }

  private Long insertAnswer(Long postId, Long userId, String content, LocalDateTime createdAt) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        conn -> {
          PreparedStatement ps =
              conn.prepareStatement(
                  "insert into answers (post_id, user_id, content, is_accepted, created_at, updated_at)"
                      + " values (?, ?, ?, ?, ?, ?)",
                  new String[] {"id"});
          ps.setLong(1, postId);
          ps.setLong(2, userId);
          ps.setString(3, content);
          ps.setBoolean(4, false);
          ps.setTimestamp(5, Timestamp.valueOf(createdAt));
          ps.setTimestamp(6, Timestamp.valueOf(createdAt));
          return ps;
        },
        keyHolder);
    return keyHolder.getKey().longValue();
  }

  private void insertQuestionProjection(
      Long postId,
      Long askerUserId,
      String questionContent,
      int answerCount,
      QnaQuestionState state,
      LocalDateTime createdAt) {
    jdbcTemplate.update(
        "insert into web3_qna_questions (post_id, question_id, asker_user_id, token_address,"
            + " reward_amount_wei, question_hash, accepted_answer_id, answer_count, state, created_at, updated_at)"
            + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        postId,
        QnaEscrowIdCodec.questionId(postId),
        askerUserId,
        TOKEN_ADDRESS,
        REWARD_AMOUNT_WEI,
        QnaContentHashFactory.hash(questionContent),
        QnaEscrowIdCodec.zeroBytes32(),
        answerCount,
        state.code(),
        Timestamp.valueOf(createdAt),
        Timestamp.valueOf(createdAt));
  }

  private void insertAnswerProjection(
      Long postId,
      Long answerId,
      Long responderUserId,
      String answerContent,
      LocalDateTime createdAt) {
    jdbcTemplate.update(
        "insert into web3_qna_answers (answer_id, post_id, question_id, answer_key, responder_user_id,"
            + " content_hash, accepted, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        answerId,
        postId,
        QnaEscrowIdCodec.questionId(postId),
        QnaEscrowIdCodec.answerId(answerId),
        responderUserId,
        QnaContentHashFactory.hash(answerContent),
        false,
        Timestamp.valueOf(createdAt),
        Timestamp.valueOf(createdAt));
  }

  private Map<String, Object> latestExecutionIntent(Long postId) {
    return jdbcTemplate.queryForMap(
        "select action_type, status, public_id from web3_execution_intents"
            + " where resource_id = ? order by created_at desc limit 1",
        postId.toString());
  }

  private String latestExecutionIntentStatus(Long postId) {
    return jdbcTemplate.queryForObject(
        "select status from web3_execution_intents where resource_id = ? order by created_at desc limit 1",
        String.class,
        postId.toString());
  }

  private String unsignedTxSnapshot(String publicId) {
    return jdbcTemplate.queryForObject(
        "select unsigned_tx_snapshot from web3_execution_intents where public_id = ?",
        String.class,
        publicId);
  }

  private String postStatus(Long postId) {
    return jdbcTemplate.queryForObject(
        "select status from posts where id = ?", String.class, postId);
  }

  private Long acceptedAnswerId(Long postId) {
    return jdbcTemplate.queryForObject(
        "select accepted_answer_id from posts where id = ?", Long.class, postId);
  }

  private boolean localAnswerAccepted(Long answerId) {
    Boolean accepted =
        jdbcTemplate.queryForObject(
            "select is_accepted from answers where id = ?", Boolean.class, answerId);
    return Boolean.TRUE.equals(accepted);
  }

  private int questionState(Long postId) {
    Integer state =
        jdbcTemplate.queryForObject(
            "select state from web3_qna_questions where post_id = ?", Integer.class, postId);
    return state == null ? -1 : state;
  }

  private boolean answerProjectionAccepted(Long answerId) {
    Boolean accepted =
        jdbcTemplate.queryForObject(
            "select accepted from web3_qna_answers where answer_id = ?", Boolean.class, answerId);
    return Boolean.TRUE.equals(accepted);
  }

  private Long transactionNonce(Long transactionId) {
    return jdbcTemplate.queryForObject(
        "select nonce from web3_transactions where id = ?", Long.class, transactionId);
  }

  private boolean postExists(Long postId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from posts where id = ?", Integer.class, postId);
    return count != null && count > 0;
  }

  private int countAdminAudit(String actionType, String targetId, Long operatorId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from admin_action_audits where action_type = ? and target_id = ? and operator_id = ?",
            Integer.class,
            actionType,
            targetId,
            operatorId);
    return count == null ? 0 : count;
  }

  private ExecutionTransactionGatewayPort.TransactionRecord seedTransactionRecord(
      long transactionId, ExecutionTransactionGatewayPort.CreateTransactionCommand command) {
    LocalDateTime now = LocalDateTime.now().minusMinutes(1);
    jdbcTemplate.update(
        "insert into web3_transactions ("
            + "id, idempotency_key, reference_type, reference_id, from_user_id, to_user_id, "
            + "from_address, to_address, amount_wei, nonce, tx_type, status, created_at, updated_at"
            + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        transactionId,
        command.idempotencyKey() + "-" + UUID.randomUUID(),
        command.referenceType().name(),
        command.referenceId(),
        command.fromUserId(),
        command.toUserId(),
        command.fromAddress(),
        command.toAddress(),
        command.amountWei(),
        command.nonce(),
        command.txType().name(),
        command.status().name(),
        Timestamp.valueOf(now),
        Timestamp.valueOf(now));
    return new ExecutionTransactionGatewayPort.TransactionRecord(
        transactionId, ExecutionTransactionStatus.CREATED, null);
  }

  private record AdminUser(Long userId, String accessToken) {}

  private record SeededSettlementScenario(Long postId, Long answerId) {}
}
