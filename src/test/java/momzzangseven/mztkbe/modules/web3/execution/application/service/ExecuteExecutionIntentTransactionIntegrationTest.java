package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransferException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.Eip1559TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionEip7702GatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionTransactionGatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionChainIdPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionRetryPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionSponsorKeyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionSponsorWalletConfigPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionSponsorWalletConfig;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.persistence.entity.Web3ExecutionIntentEntity;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.persistence.repository.Web3ExecutionIntentJpaRepository;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@TestPropertySource(
    properties = {
      "web3.reward-token.enabled=true",
      "web3.eip7702.enabled=true",
      "spring.datasource.url=jdbc:h2:mem:execute-intent-tx-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL"
    })
@SpringBootTest
@DisplayName("ExecuteExecutionIntent 트랜잭션 통합 테스트")
class ExecuteExecutionIntentTransactionIntegrationTest {

  private static final Long REQUESTER_USER_ID = 7L;
  private static final String POST_ID = "12";
  private static final String INTENT_ID = "intent-expired-question-create";
  private static final String EIP1559_STALE_INTENT_ID = "intent-stale-eip1559";
  private static final String EIP7702_STALE_INTENT_ID = "intent-stale-eip7702";
  private static final String HOOK_SETUP_FAILURE_INTENT_ID = "intent-hook-setup-failure";
  private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");
  private static final Instant FIXED_NOW = Instant.parse("2026-04-15T01:00:00Z");
  private static final LocalDateTime CREATED_AT = LocalDateTime.ofInstant(FIXED_NOW, APP_ZONE);
  private static final LocalDateTime ACTIVE_EXPIRES_AT = CREATED_AT.plusMinutes(60);

  @Autowired private ExecuteExecutionIntentUseCase executeExecutionIntentUseCase;
  @Autowired private Web3ExecutionIntentJpaRepository executionIntentRepository;
  @Autowired private EntityManager entityManager;

  @MockitoBean private ExecutionTransactionGatewayPort executionTransactionGatewayPort;
  @MockitoBean private LoadExecutionSponsorKeyPort loadExecutionSponsorKeyPort;
  @MockitoBean private ExecutionEip7702GatewayPort executionEip7702GatewayPort;
  @MockitoBean private Eip1559TransactionCodecPort eip1559TransactionCodecPort;
  @MockitoBean private LoadExecutionChainIdPort loadExecutionChainIdPort;
  @MockitoBean private LoadExecutionSponsorWalletConfigPort loadExecutionSponsorWalletConfigPort;
  @MockitoBean private LoadExecutionRetryPolicyPort loadExecutionRetryPolicyPort;
  @MockitoBean private SponsorDailyUsagePersistencePort sponsorDailyUsagePersistencePort;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.application.port.in
          .MarkTransactionSucceededUseCase
      markTransactionSucceededUseCase;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionReceiptWorker
      transactionReceiptWorker;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionIssuerWorker
      transactionIssuerWorker;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .SignedRecoveryWorker
      signedRecoveryWorker;

  @TestConfiguration
  static class FixedClockConfig {

    @Bean
    @Primary
    Clock fixedAppClock() {
      return Clock.fixed(FIXED_NOW, APP_ZONE);
    }
  }

  @AfterEach
  void tearDown() {
    executionIntentRepository.deleteAll();
  }

  @Test
  @DisplayName("만료된 question-create execute가 WEB3_013을 반환해도 intent 상태는 EXPIRED로 커밋되어야 한다")
  void executeExpiredQuestionCreateIntent_persistsExpiredStatusForRecoverCreate() {
    // given
    executionIntentRepository.saveAndFlush(expiredQuestionCreateIntent());
    entityManager.clear();

    // when & then
    assertThatThrownBy(
            () ->
                executeExecutionIntentUseCase.execute(
                    new ExecuteExecutionIntentCommand(
                        REQUESTER_USER_ID, INTENT_ID, null, null, "0xsigned")))
        .isInstanceOf(Web3TransferException.class)
        .extracting(ex -> ((Web3TransferException) ex).getCode())
        .isEqualTo(ErrorCode.EXECUTION_INTENT_EXPIRED.getCode());

    entityManager.clear();
    Web3ExecutionIntentEntity reloaded =
        executionIntentRepository.findByPublicId(INTENT_ID).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(ExecutionIntentStatus.EXPIRED);
    assertThat(reloaded.getLastErrorCode()).isEqualTo(ErrorCode.EXECUTION_INTENT_EXPIRED.name());
  }

  @Test
  @DisplayName("EIP1559 nonce stale execute가 WEB3_014를 반환해도 NONCE_STALE로 커밋되어야 한다")
  void executeEip1559NonceStale_persistsNonceStaleStatus() {
    // given
    executionIntentRepository.saveAndFlush(
        questionCreateIntent(EIP1559_STALE_INTENT_ID, ExecutionMode.EIP1559, ACTIVE_EXPIRES_AT));
    entityManager.clear();

    UnsignedTxSnapshot snapshot =
        new UnsignedTxSnapshot(
            1L,
            "0x1111111111111111111111111111111111111111",
            "0x2222222222222222222222222222222222222222",
            BigInteger.ZERO,
            "0x1234",
            1L,
            BigInteger.valueOf(21_000),
            BigInteger.valueOf(1_000_000_000),
            BigInteger.valueOf(2_000_000_000));
    when(eip1559TransactionCodecPort.decodeAndVerify(any(), any(), any()))
        .thenReturn(
            new Eip1559TransactionCodecPort.DecodedSignedTransaction(
                "0xsigned",
                "0xhash",
                "0x1111111111111111111111111111111111111111",
                snapshot,
                "0x" + "b".repeat(64)));
    when(executionEip7702GatewayPort.loadPendingAccountNonce(
            "0x1111111111111111111111111111111111111111"))
        .thenReturn(BigInteger.valueOf(2));

    // when & then
    assertThatThrownBy(
            () ->
                executeExecutionIntentUseCase.execute(
                    new ExecuteExecutionIntentCommand(
                        REQUESTER_USER_ID, EIP1559_STALE_INTENT_ID, null, null, "0xsigned")))
        .isInstanceOf(Web3TransferException.class)
        .extracting(ex -> ((Web3TransferException) ex).getCode())
        .isEqualTo(ErrorCode.NONCE_STALE_RECREATE_REQUIRED.getCode());

    entityManager.clear();
    Web3ExecutionIntentEntity reloaded =
        executionIntentRepository.findByPublicId(EIP1559_STALE_INTENT_ID).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(ExecutionIntentStatus.NONCE_STALE);
    assertThat(reloaded.getLastErrorCode())
        .isEqualTo(ErrorCode.NONCE_STALE_RECREATE_REQUIRED.name());
  }

  @Test
  @DisplayName("EIP7702 authority nonce mismatch가 WEB3_006을 반환해도 NONCE_STALE로 커밋되어야 한다")
  void executeEip7702AuthorityNonceMismatch_persistsNonceStaleStatus() {
    // given
    executionIntentRepository.saveAndFlush(
        questionCreateIntent(EIP7702_STALE_INTENT_ID, ExecutionMode.EIP7702, ACTIVE_EXPIRES_AT));
    entityManager.clear();

    when(loadExecutionSponsorWalletConfigPort.loadSponsorWalletConfig())
        .thenReturn(new ExecutionSponsorWalletConfig("alias", "kek"));
    when(loadExecutionSponsorKeyPort.loadByAlias("alias", "kek"))
        .thenReturn(
            Optional.of(
                new LoadExecutionSponsorKeyPort.ExecutionSponsorKey(
                    "0x6666666666666666666666666666666666666666", "0x" + "7".repeat(64))));
    when(loadExecutionChainIdPort.loadChainId()).thenReturn(1L);
    when(executionEip7702GatewayPort.toAuthorizationTuple(anyLong(), any(), any(), any()))
        .thenReturn(
            new ExecutionEip7702GatewayPort.AuthorizationTuple(
                BigInteger.ONE,
                "0x3333333333333333333333333333333333333333",
                BigInteger.ONE,
                BigInteger.ZERO,
                BigInteger.ONE,
                BigInteger.TWO));
    when(executionEip7702GatewayPort.hashCalls(any())).thenReturn("0x" + "9".repeat(64));
    when(executionEip7702GatewayPort.verifyAuthorizationSigner(
            anyLong(), any(), any(), any(), any()))
        .thenReturn(true);
    when(executionEip7702GatewayPort.loadPendingAccountNonce(
            "0x1111111111111111111111111111111111111111"))
        .thenReturn(BigInteger.valueOf(2));

    // when & then
    assertThatThrownBy(
            () ->
                executeExecutionIntentUseCase.execute(
                    new ExecuteExecutionIntentCommand(
                        REQUESTER_USER_ID, EIP7702_STALE_INTENT_ID, "0xauth", "0xsubmit", null)))
        .isInstanceOf(Web3TransferException.class)
        .extracting(ex -> ((Web3TransferException) ex).getCode())
        .isEqualTo(ErrorCode.AUTH_NONCE_MISMATCH.getCode());

    entityManager.clear();
    Web3ExecutionIntentEntity reloaded =
        executionIntentRepository.findByPublicId(EIP7702_STALE_INTENT_ID).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(ExecutionIntentStatus.NONCE_STALE);
    assertThat(reloaded.getLastErrorCode()).isEqualTo(ErrorCode.AUTH_NONCE_MISMATCH.name());
  }

  @Test
  @DisplayName("termination hook setup이 실패해도 만료 intent 상태는 EXPIRED로 커밋되어야 한다")
  void executeExpiredIntent_persistsExpiredStatusWhenTerminationHookSetupFails() {
    // given
    executionIntentRepository.saveAndFlush(
        questionCreateIntent(
            HOOK_SETUP_FAILURE_INTENT_ID,
            ExecutionMode.EIP1559,
            CREATED_AT.minusMinutes(1),
            ExecutionActionType.MARKETPLACE_CLASS_PURCHASE));
    entityManager.clear();

    // when & then
    assertThatThrownBy(
            () ->
                executeExecutionIntentUseCase.execute(
                    new ExecuteExecutionIntentCommand(
                        REQUESTER_USER_ID, HOOK_SETUP_FAILURE_INTENT_ID, null, null, "0xsigned")))
        .isInstanceOf(Web3TransferException.class)
        .extracting(ex -> ((Web3TransferException) ex).getCode())
        .isEqualTo(ErrorCode.EXECUTION_INTENT_EXPIRED.getCode());

    entityManager.clear();
    Web3ExecutionIntentEntity reloaded =
        executionIntentRepository.findByPublicId(HOOK_SETUP_FAILURE_INTENT_ID).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(ExecutionIntentStatus.EXPIRED);
    assertThat(reloaded.getLastErrorCode()).isEqualTo(ErrorCode.EXECUTION_INTENT_EXPIRED.name());
  }

  @Test
  @DisplayName("일반 validation 예외는 terminal 상태로 커밋하지 않는다")
  void executeValidationFailure_doesNotCommitTerminalStatus() {
    // given
    executionIntentRepository.saveAndFlush(
        questionCreateIntent(EIP1559_STALE_INTENT_ID, ExecutionMode.EIP1559, ACTIVE_EXPIRES_AT));
    entityManager.clear();

    // when & then
    assertThatThrownBy(
            () ->
                executeExecutionIntentUseCase.execute(
                    new ExecuteExecutionIntentCommand(
                        REQUESTER_USER_ID, EIP1559_STALE_INTENT_ID, null, null, null)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("signedRawTransaction is required");

    entityManager.clear();
    Web3ExecutionIntentEntity reloaded =
        executionIntentRepository.findByPublicId(EIP1559_STALE_INTENT_ID).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(ExecutionIntentStatus.AWAITING_SIGNATURE);
    assertThat(reloaded.getLastErrorCode()).isNull();
  }

  private Web3ExecutionIntentEntity expiredQuestionCreateIntent() {
    return questionCreateIntent(INTENT_ID, ExecutionMode.EIP1559, CREATED_AT.minusMinutes(1));
  }

  private Web3ExecutionIntentEntity questionCreateIntent(
      String publicId, ExecutionMode mode, LocalDateTime expiresAt) {
    return questionCreateIntent(publicId, mode, expiresAt, ExecutionActionType.QNA_QUESTION_CREATE);
  }

  private Web3ExecutionIntentEntity questionCreateIntent(
      String publicId,
      ExecutionMode mode,
      LocalDateTime expiresAt,
      ExecutionActionType actionType) {
    return Web3ExecutionIntentEntity.builder()
        .publicId(publicId)
        .rootIdempotencyKey(
            "qna:"
                + QnaExecutionActionType.QNA_QUESTION_CREATE.name().toLowerCase()
                + ":"
                + REQUESTER_USER_ID
                + ":"
                + POST_ID
                + ":"
                + publicId)
        .attemptNo(1)
        .resourceType(ExecutionResourceType.QUESTION)
        .resourceId(POST_ID)
        .actionType(actionType)
        .requesterUserId(REQUESTER_USER_ID)
        .mode(mode)
        .status(ExecutionIntentStatus.AWAITING_SIGNATURE)
        .payloadHash("0x" + "a".repeat(64))
        .payloadSnapshotJson(questionCreatePayloadJson())
        .authorityAddress(
            mode == ExecutionMode.EIP7702 ? "0x1111111111111111111111111111111111111111" : null)
        .authorityNonce(mode == ExecutionMode.EIP7702 ? 1L : null)
        .delegateTarget(
            mode == ExecutionMode.EIP7702 ? "0x3333333333333333333333333333333333333333" : null)
        .expiresAt(expiresAt)
        .authorizationPayloadHash(mode == ExecutionMode.EIP7702 ? "0x" + "c".repeat(64) : null)
        .executionDigest(mode == ExecutionMode.EIP7702 ? "0x" + "d".repeat(64) : null)
        .unsignedTxSnapshot(mode == ExecutionMode.EIP1559 ? unsignedTxSnapshotJson() : null)
        .unsignedTxFingerprint(mode == ExecutionMode.EIP1559 ? "0x" + "b".repeat(64) : null)
        .reservedSponsorCostWei(BigInteger.ZERO)
        .sponsorUsageDateKst(LocalDate.of(2026, 4, 15))
        .createdAt(CREATED_AT)
        .updatedAt(CREATED_AT)
        .build();
  }

  private String unsignedTxSnapshotJson() {
    return """
        {"chainId":1,"fromAddress":"0x1111111111111111111111111111111111111111","toAddress":"0x2222222222222222222222222222222222222222","valueWei":0,"data":"0x1234","expectedNonce":1,"gasLimit":21000,"maxPriorityFeePerGas":1000000000,"maxFeePerGas":2000000000}
        """;
  }

  private String questionCreatePayloadJson() {
    return """
        {"actionType":"QNA_QUESTION_CREATE","postId":12,"answerId":null,"authorityAddress":"0x1111111111111111111111111111111111111111","tokenAddress":"0x3333333333333333333333333333333333333333","amountWei":50000000000000000000,"questionHash":"0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","contentHash":null,"callTarget":"0x4444444444444444444444444444444444444444","callData":"0x1234"}
        """;
  }
}
