package momzzangseven.mztkbe.integration.e2e.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.MarkExecutionIntentFailedOnchainUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.MarkExecutionIntentSucceededUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.RunInternalExecutionBatchUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionEip1559SigningPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionEip7702GatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionTransactionGatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadSponsorTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.VerifyTreasuryWalletForSignPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionPayload;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraft;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaUnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.RunQnaAutoAcceptBatchUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.BuildQnaAdminExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAcceptContextPort;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaContentHashFactory;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdCodec;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaQuestionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@TestPropertySource(
    properties = {
      "web3.reward-token.enabled=true",
      "web3.eip7702.enabled=true",
      "web3.execution.internal.enabled=true",
      "web3.qna.auto-accept.enabled=true",
    })
@Tag("e2e")
@DisplayName("[E2E] QnA auto-accept scheduler flow")
class QnaAutoAcceptE2ETest extends E2ETestBase {

  private static final Instant NOW = Instant.parse("2026-04-17T01:00:00Z");
  private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");
  private static final String SPONSOR_ADDRESS = "0xd799cd2b5258edc2157bec7e2cd069f31f2678c2";
  private static final String CALL_TARGET = "0x0000000000000000000000000000000000000002";

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private RunQnaAutoAcceptBatchUseCase runQnaAutoAcceptBatchUseCase;
  @Autowired private LoadQnaAcceptContextPort loadQnaAcceptContextPort;
  @Autowired private RunInternalExecutionBatchUseCase runInternalExecutionBatchUseCase;
  @Autowired private MarkExecutionIntentSucceededUseCase markExecutionIntentSucceededUseCase;
  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired
  private MarkExecutionIntentFailedOnchainUseCase markExecutionIntentFailedOnchainUseCase;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private BuildQnaAdminExecutionDraftPort buildQnaAdminExecutionDraftPort;
  @MockitoBean private ExecutionEip1559SigningPort executionEip1559SigningPort;
  @MockitoBean private ExecutionTransactionGatewayPort executionTransactionGatewayPort;
  @MockitoBean private ExecutionEip7702GatewayPort executionEip7702GatewayPort;
  @MockitoBean private LoadSponsorTreasuryWalletPort loadSponsorTreasuryWalletPort;
  @MockitoBean private VerifyTreasuryWalletForSignPort verifyTreasuryWalletForSignPort;

  @BeforeEach
  void setUp() {
    org.mockito.BDDMockito.given(loadSponsorTreasuryWalletPort.load())
        .willReturn(
            Optional.of(
                new TreasuryWalletInfo(
                    "test-sponsor", "alias/test-sponsor", SPONSOR_ADDRESS, true)));
    org.mockito.BDDMockito.willDoNothing()
        .given(verifyTreasuryWalletForSignPort)
        .verify(org.mockito.ArgumentMatchers.anyString());
    org.mockito.BDDMockito.given(
            executionEip7702GatewayPort.loadPendingAccountNonce(SPONSOR_ADDRESS))
        .willReturn(BigInteger.valueOf(21L));
    org.mockito.BDDMockito.given(executionEip1559SigningPort.sign(any()))
        .willReturn(new ExecutionEip1559SigningPort.SignedTransaction("0xsigned", "0xhash"));
    org.mockito.BDDMockito.willAnswer(invocation -> draft(invocation.getArgument(0)))
        .given(buildQnaAdminExecutionDraftPort)
        .build(any());
  }

  @Test
  @DisplayName("overdue first responder is auto-accepted and admin-settled on success")
  void autoAccept_success_confirmsAcceptedAnswer() {
    TestUser asker = signupAndLogin("auto-accept-asker");
    TestUser responder = signupAndLogin("auto-accept-responder");
    SeededScenario scenario = seedOverdueScenario(asker.userId(), responder.userId());

    org.mockito.BDDMockito.given(executionTransactionGatewayPort.createAndFlush(any()))
        .willAnswer(
            invocation ->
                seedTransactionRecord(
                    701L,
                    invocation.getArgument(
                        0, ExecutionTransactionGatewayPort.CreateTransactionCommand.class)));
    org.mockito.BDDMockito.given(executionTransactionGatewayPort.broadcast("0xsigned"))
        .willReturn(
            new ExecutionTransactionGatewayPort.BroadcastResult(true, "0xhash701", null, "main"));

    var scheduleResult = runQnaAutoAcceptBatchUseCase.runBatch(NOW);

    assertThat(scheduleResult.scheduledCount()).isEqualTo(1);
    assertThat(postStatus(scenario.postId())).isEqualTo("PENDING_ACCEPT");
    assertThat(localAnswerAccepted(scenario.answerId())).isFalse();
    assertThat(latestExecutionIntentStatus(scenario.postId())).isEqualTo("AWAITING_SIGNATURE");

    var internalResult = runInternalExecutionBatchUseCase.runBatch(NOW);

    assertThat(internalResult.executedCount()).isEqualTo(1);
    assertThat(internalResult.pendingCount()).isEqualTo(1);
    assertThat(latestExecutionIntentStatus(scenario.postId())).isEqualTo("PENDING_ONCHAIN");

    markExecutionIntentSucceededUseCase.execute(701L);

    assertThat(postStatus(scenario.postId())).isEqualTo("RESOLVED");
    assertThat(acceptedAnswerId(scenario.postId())).isEqualTo(scenario.answerId());
    assertThat(localAnswerAccepted(scenario.answerId())).isTrue();
    assertThat(latestExecutionIntentStatus(scenario.postId())).isEqualTo("CONFIRMED");
    assertThat(questionState(scenario.postId())).isEqualTo(QnaQuestionState.ADMIN_SETTLED.code());
    assertThat(answerProjectionAccepted(scenario.answerId())).isTrue();
  }

  @Test
  @DisplayName("non-retryable onchain failure rolls pending accept back to OPEN")
  void autoAccept_nonRetryableFailure_rollsBackPendingAccept() {
    TestUser asker = signupAndLogin("auto-accept-fail-asker");
    TestUser responder = signupAndLogin("auto-accept-fail-responder");
    SeededScenario scenario = seedOverdueScenario(asker.userId(), responder.userId());

    org.mockito.BDDMockito.given(executionTransactionGatewayPort.createAndFlush(any()))
        .willAnswer(
            invocation ->
                seedTransactionRecord(
                    702L,
                    invocation.getArgument(
                        0, ExecutionTransactionGatewayPort.CreateTransactionCommand.class)));
    org.mockito.BDDMockito.given(executionTransactionGatewayPort.broadcast("0xsigned"))
        .willReturn(
            new ExecutionTransactionGatewayPort.BroadcastResult(true, "0xhash702", null, "main"));

    runQnaAutoAcceptBatchUseCase.runBatch(NOW);
    runInternalExecutionBatchUseCase.runBatch(NOW);
    markExecutionIntentFailedOnchainUseCase.execute(702L, "TREASURY_TOKEN_INSUFFICIENT");

    assertThat(postStatus(scenario.postId())).isEqualTo("OPEN");
    assertThat(acceptedAnswerId(scenario.postId())).isNull();
    assertThat(localAnswerAccepted(scenario.answerId())).isFalse();
    assertThat(latestExecutionIntentStatus(scenario.postId())).isEqualTo("FAILED_ONCHAIN");
    assertThat(questionState(scenario.postId())).isEqualTo(QnaQuestionState.ANSWERED.code());
    assertThat(answerProjectionAccepted(scenario.answerId())).isFalse();
  }

  @Test
  @DisplayName("loadForUpdate blocks on answer lock before locking post and then returns context")
  void loadForUpdate_whenAnswerLockHeld_blocksBeforePostLockAndThenReturnsContext()
      throws Exception {
    TestUser asker = signupAndLogin("auto-accept-lock-asker");
    TestUser responder = signupAndLogin("auto-accept-lock-responder");
    SeededScenario scenario = seedOverdueScenario(asker.userId(), responder.userId());

    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch answerLockHeld = new CountDownLatch(1);
    CountDownLatch scheduleStarted = new CountDownLatch(1);
    CountDownLatch postLockAcquired = new CountDownLatch(1);
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

    try {
      Future<?> holderFuture =
          executor.submit(
              () -> {
                transactionTemplate.executeWithoutResult(
                    status -> {
                      jdbcTemplate.queryForMap(
                          "SELECT id FROM answers WHERE id = ? FOR UPDATE", scenario.answerId());
                      answerLockHeld.countDown();
                      awaitLatch(scheduleStarted, "scheduleStarted");
                      pause(300L);
                      jdbcTemplate.queryForMap(
                          "SELECT id FROM posts WHERE id = ? FOR UPDATE", scenario.postId());
                      postLockAcquired.countDown();
                      pause(200L);
                    });
                return null;
              });

      Future<LoadQnaAcceptContextPort.QnaAcceptContext> loadContextFuture =
          executor.submit(
              () -> {
                awaitLatch(answerLockHeld, "answerLockHeld");
                scheduleStarted.countDown();
                return transactionTemplate.execute(
                    status ->
                        loadQnaAcceptContextPort
                            .loadForUpdate(scenario.postId(), scenario.answerId())
                            .orElseThrow());
              });

      assertThat(postLockAcquired.await(5, TimeUnit.SECONDS))
          .as("loadForUpdate should wait on answer before it can attempt the post lock")
          .isTrue();

      var context = loadContextFuture.get(10, TimeUnit.SECONDS);
      holderFuture.get(10, TimeUnit.SECONDS);

      assertThat(context.postId()).isEqualTo(scenario.postId());
      assertThat(context.answerId()).isEqualTo(scenario.answerId());
      assertThat(context.requesterUserId()).isEqualTo(asker.userId());
      assertThat(context.answerWriterUserId()).isEqualTo(responder.userId());
      assertThat(context.questionContent()).isEqualTo(scenario.questionContent());
      assertThat(context.answerContent()).isEqualTo(scenario.answerContent());
    } finally {
      executor.shutdownNow();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  private SeededScenario seedOverdueScenario(Long askerUserId, Long responderUserId) {
    String questionContent = "7일 지난 온체인 질문";
    String answerContent = "가장 먼저 달린 답변";
    LocalDateTime answerCreatedAt =
        LocalDateTime.ofInstant(NOW, APP_ZONE).minusDays(8).withHour(10).withMinute(0);
    Long postId = insertQuestionPost(askerUserId, "auto accept question", questionContent);
    Long answerId = insertAnswer(postId, responderUserId, answerContent, answerCreatedAt);
    insertQuestionProjection(
        postId, askerUserId, questionContent, 1, answerCreatedAt.minusHours(1));
    insertAnswerProjection(postId, answerId, responderUserId, answerContent, answerCreatedAt);
    return new SeededScenario(postId, answerId, questionContent, answerContent);
  }

  private void awaitLatch(CountDownLatch latch, String label) {
    try {
      assertThat(latch.await(5, TimeUnit.SECONDS)).as(label + " should be released").isTrue();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while waiting for " + label, e);
    }
  }

  private void pause(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while pausing", e);
    }
  }

  private Long insertQuestionPost(Long userId, String title, String content) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    LocalDateTime now = LocalDateTime.ofInstant(NOW, APP_ZONE).minusDays(8);
    jdbcTemplate.update(
        conn -> {
          PreparedStatement ps =
              conn.prepareStatement(
                  "insert into posts (user_id, type, title, content, reward, status, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?)",
                  new String[] {"id"});
          ps.setLong(1, userId);
          ps.setString(2, "QUESTION");
          ps.setString(3, title);
          ps.setString(4, content);
          ps.setLong(5, 50L);
          ps.setString(6, "OPEN");
          ps.setTimestamp(7, Timestamp.valueOf(now));
          ps.setTimestamp(8, Timestamp.valueOf(now));
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
                  "insert into answers (post_id, user_id, content, is_accepted, created_at, updated_at) values (?, ?, ?, ?, ?, ?)",
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

  private ExecutionTransactionGatewayPort.TransactionRecord seedTransactionRecord(
      long transactionId, ExecutionTransactionGatewayPort.CreateTransactionCommand command) {
    LocalDateTime now = LocalDateTime.ofInstant(NOW, APP_ZONE);
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

  private void insertQuestionProjection(
      Long postId,
      Long askerUserId,
      String questionContent,
      int answerCount,
      LocalDateTime createdAt) {
    jdbcTemplate.update(
        "insert into web3_qna_questions (post_id, question_id, asker_user_id, token_address, reward_amount_wei, question_hash, accepted_answer_id, answer_count, state, created_at, updated_at) "
            + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        postId,
        QnaEscrowIdCodec.questionId(postId),
        askerUserId,
        "0x" + "1".repeat(40),
        new BigInteger("50000000000000000000"),
        QnaContentHashFactory.hash(questionContent),
        QnaEscrowIdCodec.zeroBytes32(),
        answerCount,
        QnaQuestionState.ANSWERED.code(),
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
        "insert into web3_qna_answers (answer_id, post_id, question_id, answer_key, responder_user_id, content_hash, accepted, created_at, updated_at) "
            + "values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
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

  private QnaExecutionDraft draft(QnaEscrowExecutionRequest request)
      throws JsonProcessingException {
    QnaEscrowExecutionPayload payload =
        new QnaEscrowExecutionPayload(
            request.actionType(),
            request.postId(),
            request.answerId(),
            null,
            request.tokenAddress(),
            request.rewardAmountWei(),
            request.questionHash(),
            request.contentHash(),
            CALL_TARGET,
            "0x1234abcd");
    return new QnaExecutionDraft(
        request.resourceType(),
        request.resourceId(),
        QnaExecutionResourceStatus.PENDING_EXECUTION,
        request.actionType(),
        request.requesterUserId(),
        request.counterpartyUserId(),
        "root-auto-accept-" + request.postId() + "-" + request.answerId(),
        "0x" + "a".repeat(64),
        objectMapper.writeValueAsString(payload),
        List.of(new QnaExecutionDraftCall(CALL_TARGET, BigInteger.ZERO, "0x1234abcd")),
        false,
        null,
        null,
        null,
        null,
        new QnaUnsignedTxSnapshot(
            11155111L,
            SPONSOR_ADDRESS,
            CALL_TARGET,
            BigInteger.ZERO,
            "0x1234abcd",
            21L,
            BigInteger.valueOf(210_000),
            BigInteger.valueOf(2_000_000_000L),
            BigInteger.valueOf(30_000_000_000L)),
        "0x" + "b".repeat(64),
        LocalDateTime.ofInstant(NOW, APP_ZONE).plusMinutes(5));
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
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "select is_accepted from answers where id = ?", Boolean.class, answerId));
  }

  private boolean answerProjectionAccepted(Long answerId) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "select accepted from web3_qna_answers where answer_id = ?", Boolean.class, answerId));
  }

  private int questionState(Long postId) {
    return jdbcTemplate.queryForObject(
        "select state from web3_qna_questions where post_id = ?", Integer.class, postId);
  }

  private String latestExecutionIntentStatus(Long postId) {
    return jdbcTemplate.queryForObject(
        "select status from web3_execution_intents where resource_id = ? order by created_at desc limit 1",
        String.class,
        postId.toString());
  }

  private record SeededScenario(
      Long postId, Long answerId, String questionContent, String answerContent) {}
}
