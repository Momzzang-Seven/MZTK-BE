package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import momzzangseven.mztkbe.global.error.web3.KmsSignFailedException;
import momzzangseven.mztkbe.global.error.web3.SignatureRecoveryException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteInternalExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteInternalExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.InternalExecutionSignerGates;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.SponsorWalletGate;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.Eip1559TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionEip1559SigningPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionTransactionGatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionRetryPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.PublishExecutionIntentTerminatedPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.RunAfterCommitPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionFailureReason;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.kms.model.KmsException;

@ExtendWith(MockitoExtension.class)
class TransactionalExecuteInternalExecutionIntentDelegateTest {

  private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-17T01:00:00Z"), APP_ZONE);
  private static final LocalDateTime FIXED_NOW =
      LocalDateTime.ofInstant(FIXED_CLOCK.instant(), APP_ZONE);

  private static final String SPONSOR_ALIAS = "test-sponsor";
  private static final String SPONSOR_KMS_KEY = "alias/test-sponsor";
  private static final String SPONSOR_ADDRESS = "0x" + "4".repeat(40);

  @Mock private ExecutionIntentPersistencePort executionIntentPersistencePort;
  @Mock private ExecutionTransactionGatewayPort executionTransactionGatewayPort;
  @Mock private ExecutionEip1559SigningPort executionEip1559SigningPort;
  @Mock private Eip1559TransactionCodecPort eip1559TransactionCodecPort;
  @Mock private LoadExecutionRetryPolicyPort loadExecutionRetryPolicyPort;
  @Mock private ExecutionActionHandlerPort executionActionHandlerPort;
  @Mock private PublishExecutionIntentTerminatedPort publishExecutionIntentTerminatedPort;

  private final RunAfterCommitPort runAfterCommitPort = Runnable::run;
  private TransactionalExecuteInternalExecutionIntentDelegate delegate;
  private InternalExecutionSignerGates gate;

  @BeforeEach
  void setUp() {
    delegate =
        new TransactionalExecuteInternalExecutionIntentDelegate(
            executionIntentPersistencePort,
            executionTransactionGatewayPort,
            executionEip1559SigningPort,
            eip1559TransactionCodecPort,
            loadExecutionRetryPolicyPort,
            List.of(executionActionHandlerPort),
            publishExecutionIntentTerminatedPort,
            runAfterCommitPort,
            FIXED_CLOCK);

    SponsorWalletGate sponsorGate =
        new SponsorWalletGate(
            new TreasuryWalletInfo(SPONSOR_ALIAS, SPONSOR_KMS_KEY, SPONSOR_ADDRESS, true),
            new TreasurySigner(SPONSOR_ALIAS, SPONSOR_KMS_KEY, SPONSOR_ADDRESS));
    gate =
        new InternalExecutionSignerGates(Map.of(ExecutionActionType.QNA_ADMIN_SETTLE, sponsorGate));

    lenient()
        .when(executionActionHandlerPort.supports(ExecutionActionType.QNA_ADMIN_SETTLE))
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
    lenient()
        .when(eip1559TransactionCodecPort.computeFingerprint(any()))
        .thenReturn("0x" + "c".repeat(64));
    lenient()
        .when(executionIntentPersistencePort.update(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  private void stubClaimAndTrackUpdates(ExecutionIntent initial) {
    AtomicReference<ExecutionIntent> latest = new AtomicReference<>(initial);
    when(executionIntentPersistencePort.claimNextInternalExecutableForUpdate(any()))
        .thenReturn(Optional.of(initial));
    lenient()
        .when(executionIntentPersistencePort.findByPublicIdForUpdate(initial.getPublicId()))
        .thenAnswer(invocation -> Optional.of(latest.get()));
    when(executionIntentPersistencePort.update(any(ExecutionIntent.class)))
        .thenAnswer(
            invocation -> {
              ExecutionIntent updated = invocation.getArgument(0);
              latest.set(updated);
              return updated;
            });
  }

  private void stubSponsorNonceReservation(long nonce, Long transactionId) {
    when(executionTransactionGatewayPort.createAndFlush(any()))
        .thenReturn(
            new ExecutionTransactionGatewayPort.TransactionRecord(
                transactionId, ExecutionTransactionStatus.CREATED, null));
    when(executionTransactionGatewayPort.loadSponsorNonceSnapshot(11155111L, SPONSOR_ADDRESS))
        .thenReturn(
            new ExecutionTransactionGatewayPort.SponsorNonceSnapshot(
                nonce, nonce, nonce, nonce, nonce, nonce));
    when(executionTransactionGatewayPort.coordinateSponsorNonce(any()))
        .thenReturn(
            new ExecutionTransactionGatewayPort.SponsorNonceCoordinationRecord(
                "ISSUE_NONCE", nonce, "ISSUE_NONCE", true, 9001L, transactionId));
  }

  @Test
  void execute_returnsNotFoundWhenNoEligibleIntentExists() {
    when(executionIntentPersistencePort.claimNextInternalExecutableForUpdate(any()))
        .thenReturn(Optional.empty());

    ExecuteInternalExecutionIntentResult result =
        delegate.execute(
            new ExecuteInternalExecutionIntentCommand(
                List.of(ExecutionActionType.QNA_ADMIN_SETTLE)),
            gate);

    assertThat(result.executed()).isFalse();
    verify(executionActionHandlerPort, never()).beforeExecute(any(), any());
  }

  @Test
  void execute_quarantinesIntentWhenNoMatchingActionHandlerExists() {
    // [M-56] handler.supports(actionType) 가 모두 false → INTERNAL_ISSUER_INVALID_INTENT 로 cancel.
    ExecutionIntent intent = internalIntent();
    stubClaimAndTrackUpdates(intent);
    // Default `lenient().when(...).supports(QNA_ADMIN_SETTLE)` returns true; flip it off.
    lenient()
        .when(executionActionHandlerPort.supports(ExecutionActionType.QNA_ADMIN_SETTLE))
        .thenReturn(false);

    ExecuteInternalExecutionIntentResult result =
        delegate.execute(
            new ExecuteInternalExecutionIntentCommand(
                List.of(ExecutionActionType.QNA_ADMIN_SETTLE)),
            gate);

    assertThat(result.executed()).isTrue();
    assertThat(result.quarantined()).isTrue();
    assertThat(result.executionIntentStatus()).isEqualTo(ExecutionIntentStatus.CANCELED);
    verify(executionEip1559SigningPort, never()).sign(any());
  }

  @Test
  void execute_quarantinesIntentWhenModeIsNotEip1559() {
    // [M-57] internal issuer 는 EIP-1559 만 지원 — 다른 mode 진입 시 즉시 quarantine.
    ExecutionIntent intent = internalIntentWithMode(ExecutionMode.EIP7702);
    when(executionIntentPersistencePort.claimNextInternalExecutableForUpdate(any()))
        .thenReturn(Optional.of(intent));

    ExecuteInternalExecutionIntentResult result =
        delegate.execute(
            new ExecuteInternalExecutionIntentCommand(
                List.of(ExecutionActionType.QNA_ADMIN_SETTLE)),
            gate);

    assertThat(result.executed()).isTrue();
    assertThat(result.quarantined()).isTrue();
    assertThat(result.executionIntentStatus()).isEqualTo(ExecutionIntentStatus.CANCELED);
    verify(executionEip1559SigningPort, never()).sign(any());
  }

  @Test
  void execute_quarantinesIntentWhenUnsignedTxSnapshotIsMissing() {
    // [M-58] unsignedTxSnapshot/fingerprint 누락 → quarantine.
    ExecutionIntent intent = internalIntentWithoutSnapshot();
    when(executionIntentPersistencePort.claimNextInternalExecutableForUpdate(any()))
        .thenReturn(Optional.of(intent));

    ExecuteInternalExecutionIntentResult result =
        delegate.execute(
            new ExecuteInternalExecutionIntentCommand(
                List.of(ExecutionActionType.QNA_ADMIN_SETTLE)),
            gate);

    assertThat(result.executed()).isTrue();
    assertThat(result.quarantined()).isTrue();
    assertThat(result.executionIntentStatus()).isEqualTo(ExecutionIntentStatus.CANCELED);
    verify(executionEip1559SigningPort, never()).sign(any());
  }

  @Test
  void execute_rebindsReservedNonceWhenLocalAllocatorAdvances() {
    ExecutionIntent intent = internalIntent();
    stubClaimAndTrackUpdates(intent);
    stubSponsorNonceReservation(13L, 77L);
    when(executionEip1559SigningPort.sign(any()))
        .thenReturn(new ExecutionEip1559SigningPort.SignedTransaction("0xsigned", "0xhash"));
    when(executionTransactionGatewayPort.broadcast("0xsigned"))
        .thenReturn(
            new ExecutionTransactionGatewayPort.BroadcastResult(true, "0xhash", null, "main"));

    ExecuteInternalExecutionIntentResult result =
        delegate.execute(
            new ExecuteInternalExecutionIntentCommand(
                List.of(ExecutionActionType.QNA_ADMIN_SETTLE)),
            gate);

    assertThat(result.executed()).isTrue();
    assertThat(result.executionIntentStatus()).isEqualTo(ExecutionIntentStatus.SIGNED);
    verify(executionEip1559SigningPort)
        .sign(org.mockito.ArgumentMatchers.argThat(command -> command.nonce() == 13L));
    verify(executionEip1559SigningPort)
        .sign(
            org.mockito.ArgumentMatchers.argThat(
                command -> SPONSOR_KMS_KEY.equals(command.signer().kmsKeyId())));
    verify(executionTransactionGatewayPort)
        .createAndFlush(org.mockito.ArgumentMatchers.argThat(command -> command.nonce() == null));
  }

  @Test
  void execute_marksPendingOnchainWhenBroadcastSucceeds() {
    ExecutionIntent intent = internalIntent();
    stubClaimAndTrackUpdates(intent);
    stubSponsorNonceReservation(intent.getUnsignedTxSnapshot().expectedNonce(), 77L);
    when(executionEip1559SigningPort.sign(any()))
        .thenReturn(new ExecutionEip1559SigningPort.SignedTransaction("0xsigned", "0xhash"));
    when(executionTransactionGatewayPort.broadcast("0xsigned"))
        .thenReturn(
            new ExecutionTransactionGatewayPort.BroadcastResult(true, "0xhash", null, "main"));

    ExecuteInternalExecutionIntentResult result =
        delegate.execute(
            new ExecuteInternalExecutionIntentCommand(
                List.of(ExecutionActionType.QNA_ADMIN_SETTLE)),
            gate);

    assertThat(result.executed()).isTrue();
    assertThat(result.executionIntentStatus()).isEqualTo(ExecutionIntentStatus.SIGNED);
    assertThat(result.transactionId()).isEqualTo(77L);
    assertThat(result.transactionStatus()).isEqualTo(ExecutionTransactionStatus.SIGNED);
    assertThat(result.txHash()).isEqualTo("0xhash");
    verify(executionTransactionGatewayPort).markPending(77L, "0xhash");
  }

  @Test
  void execute_marksSignedAndSchedulesRetryWhenBroadcastFails() {
    ExecutionIntent intent = internalIntent();
    stubClaimAndTrackUpdates(intent);
    stubSponsorNonceReservation(intent.getUnsignedTxSnapshot().expectedNonce(), 78L);
    when(executionEip1559SigningPort.sign(any()))
        .thenReturn(new ExecutionEip1559SigningPort.SignedTransaction("0xsigned", "0xhash"));
    when(executionTransactionGatewayPort.broadcast("0xsigned"))
        .thenReturn(
            new ExecutionTransactionGatewayPort.BroadcastResult(
                false, null, "RPC_UNAVAILABLE", "main"));

    ExecuteInternalExecutionIntentResult result =
        delegate.execute(
            new ExecuteInternalExecutionIntentCommand(
                List.of(ExecutionActionType.QNA_ADMIN_SETTLE)),
            gate);

    assertThat(result.executed()).isTrue();
    assertThat(result.executionIntentStatus()).isEqualTo(ExecutionIntentStatus.SIGNED);
    assertThat(result.transactionId()).isEqualTo(78L);
    assertThat(result.transactionStatus()).isEqualTo(ExecutionTransactionStatus.SIGNED);
    verify(executionTransactionGatewayPort).scheduleRetry(any(), any(), any());
  }

  @Test
  void execute_quarantinesIntentWhenSignerDoesNotMatch() {
    ExecutionIntent intent = internalIntentWithSigner("0x" + "5".repeat(40));
    when(executionIntentPersistencePort.claimNextInternalExecutableForUpdate(any()))
        .thenReturn(Optional.of(intent));

    ExecuteInternalExecutionIntentResult result =
        delegate.execute(
            new ExecuteInternalExecutionIntentCommand(
                List.of(ExecutionActionType.QNA_ADMIN_SETTLE)),
            gate);

    assertThat(result.executed()).isTrue();
    assertThat(result.quarantined()).isTrue();
    assertThat(result.executionIntentStatus()).isEqualTo(ExecutionIntentStatus.CANCELED);
    verify(publishExecutionIntentTerminatedPort)
        .publish(
            org.mockito.ArgumentMatchers.argThat(
                event ->
                    event.executionIntentId().equals("intent-admin-settle")
                        && event.terminalStatus() == ExecutionIntentStatus.CANCELED));
    verify(executionEip1559SigningPort, never()).sign(any());
  }

  @Test
  void execute_quarantinesIntentWhenKmsSignFailsWithTerminalAwsError_publishesWithKmsErrorCode() {
    ExecutionIntent intent = internalIntent();
    long expectedNonce = intent.getUnsignedTxSnapshot().expectedNonce();
    when(executionIntentPersistencePort.claimNextInternalExecutableForUpdate(any()))
        .thenReturn(Optional.of(intent));
    stubSponsorNonceReservation(expectedNonce, 77L);
    when(executionEip1559SigningPort.sign(any()))
        .thenThrow(new KmsSignFailedException("kms denied", terminalAwsKmsCause()));

    ExecuteInternalExecutionIntentResult result =
        delegate.execute(
            new ExecuteInternalExecutionIntentCommand(
                List.of(ExecutionActionType.QNA_ADMIN_SETTLE)),
            gate);

    assertThat(result.quarantined()).isTrue();
    assertThat(result.executionIntentStatus()).isEqualTo(ExecutionIntentStatus.CANCELED);
    verify(executionTransactionGatewayPort)
        .scheduleRetry(77L, ExecutionFailureReason.KMS_SIGN_FAILED_TERMINAL.name(), null);
    verify(executionTransactionGatewayPort)
        .transitionSponsorNonceSlot(
            org.mockito.ArgumentMatchers.argThat(
                command ->
                    command.toStatus().equals("DROPPED") && command.nonce() == expectedNonce));
    ArgumentCaptor<
            momzzangseven.mztkbe.modules.web3.execution.domain.event.ExecutionIntentTerminatedEvent>
        captor =
            ArgumentCaptor.forClass(
                momzzangseven.mztkbe.modules.web3.execution.domain.event
                    .ExecutionIntentTerminatedEvent.class);
    verify(publishExecutionIntentTerminatedPort).publish(captor.capture());
    assertThat(captor.getValue().failureReason())
        .isEqualTo(ExecutionFailureReason.KMS_SIGN_FAILED_TERMINAL.name());
    verify(executionTransactionGatewayPort, never()).broadcast(any());
  }

  @Test
  void execute_returnsTransientRetryWithExecutedFalseWhenKmsSignFailsTransiently() {
    ExecutionIntent intent = internalIntent();
    long expectedNonce = intent.getUnsignedTxSnapshot().expectedNonce();
    when(executionIntentPersistencePort.claimNextInternalExecutableForUpdate(any()))
        .thenReturn(Optional.of(intent));
    stubSponsorNonceReservation(expectedNonce, 77L);
    // No AWS error code → classifier returns false → transient.
    when(executionEip1559SigningPort.sign(any()))
        .thenThrow(new KmsSignFailedException("kms throttled"));

    assertThatThrownBy(
            () ->
                delegate.execute(
                    new ExecuteInternalExecutionIntentCommand(
                        List.of(ExecutionActionType.QNA_ADMIN_SETTLE)),
                    gate))
        .isInstanceOf(InternalExecutionTransientRetryException.class)
        .extracting(ex -> ((InternalExecutionTransientRetryException) ex).executionIntentStatus())
        .isEqualTo(ExecutionIntentStatus.AWAITING_SIGNATURE);
    // Critical invariant: transient path must NOT cancel intent and must NOT publish the
    // terminated event — the QnA escrow refund cascade must not fire on a recoverable hiccup.
    verify(executionIntentPersistencePort, never()).update(any());
    verify(publishExecutionIntentTerminatedPort, never()).publish(any());
    verify(executionTransactionGatewayPort, never()).broadcast(any());
  }

  @Test
  void execute_dropsReservedSlotAndContinuesWhenKmsTerminalFails() {
    ExecutionIntent intent = internalIntent();
    long expectedNonce = intent.getUnsignedTxSnapshot().expectedNonce();
    when(executionIntentPersistencePort.claimNextInternalExecutableForUpdate(any()))
        .thenReturn(Optional.of(intent));
    stubSponsorNonceReservation(expectedNonce, 77L);
    when(executionEip1559SigningPort.sign(any()))
        .thenThrow(new KmsSignFailedException("kms denied", terminalAwsKmsCause()));

    ExecuteInternalExecutionIntentResult result =
        delegate.execute(
            new ExecuteInternalExecutionIntentCommand(
                List.of(ExecutionActionType.QNA_ADMIN_SETTLE)),
            gate);

    // Terminal cancellation still completes after the reserved slot is dropped.
    assertThat(result.quarantined()).isTrue();
    assertThat(result.executionIntentStatus()).isEqualTo(ExecutionIntentStatus.CANCELED);
    verify(executionTransactionGatewayPort)
        .scheduleRetry(77L, ExecutionFailureReason.KMS_SIGN_FAILED_TERMINAL.name(), null);
    verify(executionTransactionGatewayPort)
        .transitionSponsorNonceSlot(
            org.mockito.ArgumentMatchers.argThat(
                command ->
                    command.toStatus().equals("DROPPED") && command.nonce() == expectedNonce));
  }

  @Test
  void execute_quarantinesIntentWhenSignatureRecoveryFails_publishesWithSignatureRecoveryCode() {
    ExecutionIntent intent = internalIntent();
    long expectedNonce = intent.getUnsignedTxSnapshot().expectedNonce();
    when(executionIntentPersistencePort.claimNextInternalExecutableForUpdate(any()))
        .thenReturn(Optional.of(intent));
    stubSponsorNonceReservation(expectedNonce, 77L);
    when(executionEip1559SigningPort.sign(any()))
        .thenThrow(new SignatureRecoveryException("recovery failed"));

    ExecuteInternalExecutionIntentResult result =
        delegate.execute(
            new ExecuteInternalExecutionIntentCommand(
                List.of(ExecutionActionType.QNA_ADMIN_SETTLE)),
            gate);

    assertThat(result.quarantined()).isTrue();
    assertThat(result.executionIntentStatus()).isEqualTo(ExecutionIntentStatus.CANCELED);
    verify(executionTransactionGatewayPort)
        .scheduleRetry(77L, ExecutionFailureReason.SIGNATURE_INVALID.name(), null);
    ArgumentCaptor<
            momzzangseven.mztkbe.modules.web3.execution.domain.event.ExecutionIntentTerminatedEvent>
        captor =
            ArgumentCaptor.forClass(
                momzzangseven.mztkbe.modules.web3.execution.domain.event
                    .ExecutionIntentTerminatedEvent.class);
    verify(publishExecutionIntentTerminatedPort).publish(captor.capture());
    assertThat(captor.getValue().failureReason())
        .isEqualTo(ExecutionFailureReason.SIGNATURE_INVALID.name());
  }

  @Test
  void execute_quarantinesIntentWhenSigningInputIsInvalid() {
    ExecutionIntent intent = internalIntent();
    long expectedNonce = intent.getUnsignedTxSnapshot().expectedNonce();
    when(executionIntentPersistencePort.claimNextInternalExecutableForUpdate(any()))
        .thenReturn(Optional.of(intent));
    stubSponsorNonceReservation(expectedNonce, 77L);
    when(executionEip1559SigningPort.sign(any()))
        .thenThrow(new Web3InvalidInputException("invalid EVM address: broken"));

    ExecuteInternalExecutionIntentResult result =
        delegate.execute(
            new ExecuteInternalExecutionIntentCommand(
                List.of(ExecutionActionType.QNA_ADMIN_SETTLE)),
            gate);

    assertThat(result.executed()).isTrue();
    assertThat(result.quarantined()).isTrue();
    assertThat(result.executionIntentStatus()).isEqualTo(ExecutionIntentStatus.CANCELED);
    verify(executionTransactionGatewayPort).scheduleRetry(77L, "PREVALIDATE_INVALID_COMMAND", null);
    verify(publishExecutionIntentTerminatedPort)
        .publish(
            org.mockito.ArgumentMatchers.argThat(
                event ->
                    event.executionIntentId().equals("intent-admin-settle")
                        && event.terminalStatus() == ExecutionIntentStatus.CANCELED));
    verify(executionTransactionGatewayPort, never()).broadcast(any());
  }

  private static KmsException terminalAwsKmsCause() {
    return (KmsException)
        KmsException.builder()
            .awsErrorDetails(AwsErrorDetails.builder().errorCode("AccessDeniedException").build())
            .build();
  }

  private ExecutionIntent internalIntent() {
    return internalIntentWithSigner(SPONSOR_ADDRESS);
  }

  private ExecutionIntent internalIntentWithSigner(String fromAddress) {
    return ExecutionIntent.create(
        "intent-admin-settle",
        "root-admin-settle",
        1,
        ExecutionResourceType.QUESTION,
        "101",
        ExecutionActionType.QNA_ADMIN_SETTLE,
        7L,
        22L,
        ExecutionMode.EIP1559,
        "0x" + "a".repeat(64),
        "{\"payload\":true}",
        null,
        null,
        null,
        FIXED_NOW.plusMinutes(5),
        null,
        null,
        new UnsignedTxSnapshot(
            11155111L,
            fromAddress,
            "0x" + "3".repeat(40),
            BigInteger.ZERO,
            "0x1234",
            12L,
            BigInteger.valueOf(210_000),
            BigInteger.valueOf(2_000_000_000L),
            BigInteger.valueOf(30_000_000_000L)),
        "0x" + "b".repeat(64),
        BigInteger.ZERO,
        LocalDate.of(2026, 4, 17),
        FIXED_NOW);
  }

  private ExecutionIntent internalIntentWithMode(ExecutionMode mode) {
    // For non-EIP1559 modes, bypass `create(...)` validation via toBuilder so the delegate's
    // "internal issuer supports only EIP1559" branch can be exercised end-to-end.
    return internalIntent().toBuilder().mode(mode).build();
  }

  private ExecutionIntent internalIntentWithoutSnapshot() {
    // Bypass `create(...)` validation (which requires non-null snapshot for EIP1559) via toBuilder
    // to test the defensive null-check inside the delegate.
    return internalIntent().toBuilder()
        .unsignedTxSnapshot(null)
        .unsignedTxFingerprint(null)
        .build();
  }
}
