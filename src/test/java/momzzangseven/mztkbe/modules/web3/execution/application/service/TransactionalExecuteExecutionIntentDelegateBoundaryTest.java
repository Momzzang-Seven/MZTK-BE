package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import momzzangseven.mztkbe.global.error.web3.ExecutionIntentTerminalException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.SponsorWalletGate;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.Eip1559TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionEip7702GatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionTransactionGatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionChainIdPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionRetryPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.PublishExecutionIntentTerminatedPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.RunAfterCommitPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.RunExecutionTransactionPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionReferenceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionRetryPolicy;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runtime verification of {@link TransactionalExecuteExecutionIntentDelegate}'s explicit
 * transaction-boundary semantics.
 *
 * <p>The MOM-397 invariant under test: terminal {@link ExecutionIntentTerminalException} persists
 * the upstream EXPIRED / NONCE_STALE state transition before the exception surfaces to the client.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionalExecuteExecutionIntentDelegate transaction-boundary tests")
class TransactionalExecuteExecutionIntentDelegateBoundaryTest {

  private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-15T01:00:00Z"), APP_ZONE);
  private static final LocalDateTime FIXED_NOW =
      LocalDateTime.ofInstant(FIXED_CLOCK.instant(), APP_ZONE);

  private static final String SPONSOR_ALIAS = "sponsor-treasury";
  private static final String SPONSOR_KMS_KEY_ID = "alias/sponsor-treasury";
  private static final String SPONSOR_ADDRESS = "0x" + "6".repeat(40);

  @Mock private ExecutionIntentPersistencePort executionIntentPersistencePort;
  @Mock private SponsorDailyUsagePersistencePort sponsorDailyUsagePersistencePort;
  @Mock private ExecutionTransactionGatewayPort executionTransactionGatewayPort;
  @Mock private ExecutionEip7702GatewayPort executionEip7702GatewayPort;
  @Mock private Eip1559TransactionCodecPort eip1559TransactionCodecPort;
  @Mock private LoadExecutionChainIdPort loadExecutionChainIdPort;
  @Mock private LoadExecutionRetryPolicyPort loadExecutionRetryPolicyPort;
  @Mock private ExecutionActionHandlerPort executionActionHandlerPort;
  @Mock private PublishExecutionIntentTerminatedPort publishExecutionIntentTerminatedPort;

  private final RunAfterCommitPort runAfterCommitPort = new TestRunAfterCommitPort();
  private RecordingRunExecutionTransactionPort transactionPort;
  private TransactionalExecuteExecutionIntentDelegate rawDelegate;

  @BeforeEach
  void setUp() {
    transactionPort = new RecordingRunExecutionTransactionPort();
    rawDelegate =
        new TransactionalExecuteExecutionIntentDelegate(
            executionIntentPersistencePort,
            sponsorDailyUsagePersistencePort,
            executionTransactionGatewayPort,
            executionEip7702GatewayPort,
            eip1559TransactionCodecPort,
            loadExecutionChainIdPort,
            loadExecutionRetryPolicyPort,
            List.of(executionActionHandlerPort),
            publishExecutionIntentTerminatedPort,
            runAfterCommitPort,
            transactionPort,
            FIXED_CLOCK);
    lenient()
        .when(executionActionHandlerPort.supports(ExecutionActionType.TRANSFER_SEND))
        .thenReturn(true);
    lenient()
        .when(executionActionHandlerPort.supports(any(ExecutionIntent.class)))
        .thenReturn(true);
    lenient()
        .when(executionActionHandlerPort.buildActionPlan(any()))
        .thenReturn(
            new ExecutionActionPlan(
                BigInteger.valueOf(100),
                ExecutionReferenceType.USER_TO_USER,
                List.of(new ExecutionDraftCall("0x" + "3".repeat(40), BigInteger.ZERO, "0x1234"))));
    lenient()
        .when(loadExecutionRetryPolicyPort.loadRetryPolicy())
        .thenReturn(new ExecutionRetryPolicy(30));
    lenient().when(loadExecutionChainIdPort.loadChainId()).thenReturn(11155111L);
  }

  @Test
  @DisplayName("terminal 예외(EXPIRED)는 상태 변경을 커밋한 뒤 다시 던진다 — MOM-397 invariant")
  void terminalException_commitsStateBeforeRethrow() {
    ExecutionIntent expired =
        existingEip1559Intent().toBuilder().expiresAt(FIXED_NOW.minusSeconds(1)).build();
    when(executionIntentPersistencePort.findByPublicId("intent-1"))
        .thenReturn(Optional.of(expired));
    when(executionIntentPersistencePort.findByPublicIdForUpdate("intent-1"))
        .thenReturn(Optional.of(expired));
    when(executionIntentPersistencePort.update(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    assertThatThrownBy(
            () ->
                rawDelegate.execute(
                    new ExecuteExecutionIntentCommand(7L, "intent-1", null, null, "0xsigned"),
                    sponsorGate()))
        .isInstanceOf(ExecutionIntentTerminalException.class);

    assertThat(transactionPort.commits)
        .as("EXPIRED state transition must be committed before terminal exception is surfaced")
        .isEqualTo(1);
    assertThat(transactionPort.rollbacks).isZero();
  }

  @Test
  @DisplayName("validation 예외(intent not found)는 상태 트랜잭션을 열지 않는다")
  void validationException_doesNotOpenStateTransaction() {
    when(executionIntentPersistencePort.findByPublicId("missing-intent"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                rawDelegate.execute(
                    new ExecuteExecutionIntentCommand(7L, "missing-intent", null, null, "0xsigned"),
                    sponsorGate()))
        .isInstanceOf(Web3InvalidInputException.class);

    assertThat(transactionPort.commits).isZero();
    assertThat(transactionPort.rollbacks).isZero();
  }

  @Test
  @DisplayName("broadcast 이후 afterSubmitted 훅 실패는 제출 상태 커밋을 롤백하지 않는다")
  void afterSubmittedHookFailure_doesNotRollBackBroadcastState() {
    ExecutionIntent intent =
        existingEip1559Intent().toBuilder().expiresAt(FIXED_NOW.plusMinutes(5)).build();
    Eip1559TransactionCodecPort.DecodedSignedTransaction decoded =
        new Eip1559TransactionCodecPort.DecodedSignedTransaction(
            "0xsigned",
            "0xhash",
            intent.getUnsignedTxSnapshot().fromAddress(),
            intent.getUnsignedTxSnapshot(),
            intent.getUnsignedTxFingerprint());

    stubFindAndTrackUpdates(intent);
    when(eip1559TransactionCodecPort.decodeAndVerify(
            "0xsigned", intent.getUnsignedTxSnapshot(), intent.getUnsignedTxFingerprint()))
        .thenReturn(decoded);
    when(executionEip7702GatewayPort.loadPendingAccountNonce(
            intent.getUnsignedTxSnapshot().fromAddress()))
        .thenReturn(BigInteger.valueOf(intent.getUnsignedTxSnapshot().expectedNonce()));
    when(executionTransactionGatewayPort.createAndFlush(any()))
        .thenReturn(
            new ExecutionTransactionGatewayPort.TransactionRecord(
                202L, ExecutionTransactionStatus.CREATED, null));
    when(executionTransactionGatewayPort.broadcast("0xsigned"))
        .thenReturn(
            new ExecutionTransactionGatewayPort.BroadcastResult(true, "0xhash", "rpc-1", null));
    doThrow(new RuntimeException("hook failed"))
        .when(executionActionHandlerPort)
        .afterTransactionSubmitted(any(), any(), eq(ExecutionTransactionStatus.PENDING));

    var result =
        rawDelegate.execute(
            new ExecuteExecutionIntentCommand(7L, "intent-1", null, null, "0xsigned"),
            sponsorGate());

    assertThat(result.executionIntentStatus()).isEqualTo(ExecutionIntentStatus.SIGNED);
    assertThat(result.transactionId()).isEqualTo(202L);
    assertThat(transactionPort.commits).isEqualTo(2);
    assertThat(transactionPort.rollbacks).isZero();
    verify(executionTransactionGatewayPort).markPending(202L, "0xhash");
    verify(executionActionHandlerPort)
        .afterTransactionSubmitted(any(), any(), eq(ExecutionTransactionStatus.PENDING));
  }

  private SponsorWalletGate sponsorGate() {
    TreasuryWalletInfo info =
        new TreasuryWalletInfo(SPONSOR_ALIAS, SPONSOR_KMS_KEY_ID, SPONSOR_ADDRESS, true);
    TreasurySigner signer = new TreasurySigner(SPONSOR_ALIAS, SPONSOR_KMS_KEY_ID, SPONSOR_ADDRESS);
    return new SponsorWalletGate(info, signer);
  }

  private void stubFindAndTrackUpdates(ExecutionIntent initial) {
    AtomicReference<ExecutionIntent> latest = new AtomicReference<>(initial);
    when(executionIntentPersistencePort.findByPublicId(initial.getPublicId()))
        .thenAnswer(invocation -> Optional.of(latest.get()));
    when(executionIntentPersistencePort.findByPublicIdForUpdate(initial.getPublicId()))
        .thenAnswer(invocation -> Optional.of(latest.get()));
    when(executionIntentPersistencePort.update(any(ExecutionIntent.class)))
        .thenAnswer(
            invocation -> {
              ExecutionIntent updated = invocation.getArgument(0);
              latest.set(updated);
              return updated;
            });
  }

  private ExecutionIntent existingEip1559Intent() {
    return ExecutionIntent.create(
        "intent-1",
        "root-1",
        1,
        ExecutionResourceType.TRANSFER,
        "transfer:1",
        ExecutionActionType.TRANSFER_SEND,
        7L,
        8L,
        ExecutionMode.EIP1559,
        "0x" + "a".repeat(64),
        "{\"amountWei\":\"100\"}",
        null,
        null,
        null,
        FIXED_NOW,
        null,
        null,
        new UnsignedTxSnapshot(
            11155111L,
            "0x" + "4".repeat(40),
            "0x" + "3".repeat(40),
            BigInteger.ZERO,
            "0x1234",
            5L,
            BigInteger.valueOf(80_000),
            BigInteger.valueOf(2_000_000_000L),
            BigInteger.valueOf(50_000_000_000L)),
        "0x" + "b".repeat(64),
        BigInteger.ZERO,
        LocalDate.of(2026, 4, 15),
        FIXED_NOW);
  }

  private static class RecordingRunExecutionTransactionPort implements RunExecutionTransactionPort {

    int commits;
    int rollbacks;

    @Override
    public <T> T requiresNew(java.util.function.Supplier<T> action) {
      try {
        T result = action.get();
        commits++;
        return result;
      } catch (RuntimeException e) {
        rollbacks++;
        throw e;
      }
    }
  }

  private static class TestRunAfterCommitPort implements RunAfterCommitPort {

    private boolean runningAfterCommitCallback;

    @Override
    public void runAfterCommit(Runnable action) {
      if (canRegisterAfterCommitCallback()) {
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
              @Override
              public void afterCommit() {
                runAfterCommitCallback(action);
              }
            });
        return;
      }

      action.run();
    }

    @Override
    public void runAfterCommitWithoutTransaction(Runnable action) {
      if (canRegisterAfterCommitCallback()) {
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
              @Override
              public void afterCommit() {
                runAfterCommitCallback(action);
              }
            });
        return;
      }

      action.run();
    }

    private boolean canRegisterAfterCommitCallback() {
      return TransactionSynchronizationManager.isSynchronizationActive()
          && !runningAfterCommitCallback;
    }

    private void runAfterCommitCallback(Runnable action) {
      boolean previous = runningAfterCommitCallback;
      runningAfterCommitCallback = true;
      try {
        action.run();
      } finally {
        runningAfterCommitCallback = previous;
      }
    }
  }
}
