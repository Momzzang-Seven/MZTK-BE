package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import java.util.Optional;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.web3.KmsSignFailedException;
import momzzangseven.mztkbe.global.error.web3.SignatureRecoveryException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteInternalExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteInternalExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.SponsorWalletGate;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.Eip1559TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionEip1559SigningPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionTransactionGatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionRetryPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.PublishExecutionIntentTerminatedPort;
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

  private TransactionalExecuteInternalExecutionIntentDelegate delegate;
  private SponsorWalletGate gate;

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
            FIXED_CLOCK);

    gate =
        new SponsorWalletGate(
            new TreasuryWalletInfo(SPONSOR_ALIAS, SPONSOR_KMS_KEY, SPONSOR_ADDRESS, true),
            new TreasurySigner(SPONSOR_ALIAS, SPONSOR_KMS_KEY, SPONSOR_ADDRESS));

    lenient()
        .when(executionActionHandlerPort.supports(ExecutionActionType.QNA_ADMIN_SETTLE))
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
  void execute_rebindsReservedNonceWhenLocalAllocatorAdvances() {
    ExecutionIntent intent = internalIntent();
    when(executionIntentPersistencePort.claimNextInternalExecutableForUpdate(any()))
        .thenReturn(Optional.of(intent));
    when(executionTransactionGatewayPort.reserveNextNonce(SPONSOR_ADDRESS)).thenReturn(13L);
    when(executionEip1559SigningPort.sign(any()))
        .thenReturn(new ExecutionEip1559SigningPort.SignedTransaction("0xsigned", "0xhash"));
    when(executionTransactionGatewayPort.createAndFlush(any()))
        .thenReturn(
            new ExecutionTransactionGatewayPort.TransactionRecord(
                77L, ExecutionTransactionStatus.CREATED, null));
    when(executionTransactionGatewayPort.broadcast("0xsigned"))
        .thenReturn(
            new ExecutionTransactionGatewayPort.BroadcastResult(true, "0xhash", null, "main"));

    ExecuteInternalExecutionIntentResult result =
        delegate.execute(
            new ExecuteInternalExecutionIntentCommand(
                List.of(ExecutionActionType.QNA_ADMIN_SETTLE)),
            gate);

    assertThat(result.executed()).isTrue();
    assertThat(result.executionIntentStatus()).isEqualTo(ExecutionIntentStatus.PENDING_ONCHAIN);
    verify(executionEip1559SigningPort)
        .sign(org.mockito.ArgumentMatchers.argThat(command -> command.nonce() == 13L));
    verify(executionEip1559SigningPort)
        .sign(
            org.mockito.ArgumentMatchers.argThat(
                command -> SPONSOR_KMS_KEY.equals(command.signer().kmsKeyId())));
    verify(executionTransactionGatewayPort)
        .createAndFlush(
            org.mockito.ArgumentMatchers.argThat(command -> command.nonce().equals(13L)));
  }

  @Test
  void execute_marksPendingOnchainWhenBroadcastSucceeds() {
    ExecutionIntent intent = internalIntent();
    when(executionIntentPersistencePort.claimNextInternalExecutableForUpdate(any()))
        .thenReturn(Optional.of(intent));
    when(executionTransactionGatewayPort.reserveNextNonce(SPONSOR_ADDRESS))
        .thenReturn(intent.getUnsignedTxSnapshot().expectedNonce());
    when(executionEip1559SigningPort.sign(any()))
        .thenReturn(new ExecutionEip1559SigningPort.SignedTransaction("0xsigned", "0xhash"));
    when(executionTransactionGatewayPort.createAndFlush(any()))
        .thenReturn(
            new ExecutionTransactionGatewayPort.TransactionRecord(
                77L, ExecutionTransactionStatus.CREATED, null));
    when(executionTransactionGatewayPort.broadcast("0xsigned"))
        .thenReturn(
            new ExecutionTransactionGatewayPort.BroadcastResult(true, "0xhash", null, "main"));

    ExecuteInternalExecutionIntentResult result =
        delegate.execute(
            new ExecuteInternalExecutionIntentCommand(
                List.of(ExecutionActionType.QNA_ADMIN_SETTLE)),
            gate);

    assertThat(result.executed()).isTrue();
    assertThat(result.executionIntentStatus()).isEqualTo(ExecutionIntentStatus.PENDING_ONCHAIN);
    assertThat(result.transactionId()).isEqualTo(77L);
    assertThat(result.transactionStatus()).isEqualTo(ExecutionTransactionStatus.PENDING);
    assertThat(result.txHash()).isEqualTo("0xhash");
    verify(executionTransactionGatewayPort).markPending(77L, "0xhash");
    // Happy-path regression: nonce stays consumed; release must NOT be called.
    verify(executionTransactionGatewayPort, never()).releaseReservedNonce(any(), anyLong());
  }

  @Test
  void execute_marksSignedAndSchedulesRetryWhenBroadcastFails() {
    ExecutionIntent intent = internalIntent();
    when(executionIntentPersistencePort.claimNextInternalExecutableForUpdate(any()))
        .thenReturn(Optional.of(intent));
    when(executionTransactionGatewayPort.reserveNextNonce(SPONSOR_ADDRESS))
        .thenReturn(intent.getUnsignedTxSnapshot().expectedNonce());
    when(executionEip1559SigningPort.sign(any()))
        .thenReturn(new ExecutionEip1559SigningPort.SignedTransaction("0xsigned", "0xhash"));
    when(executionTransactionGatewayPort.createAndFlush(any()))
        .thenReturn(
            new ExecutionTransactionGatewayPort.TransactionRecord(
                78L, ExecutionTransactionStatus.CREATED, null));
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
    verify(executionTransactionGatewayPort, never()).reserveNextNonce(any());
    verify(executionEip1559SigningPort, never()).sign(any());
  }

  @Test
  void execute_quarantinesIntentWhenKmsSignFailsWithTerminalAwsError_publishesWithKmsErrorCode() {
    ExecutionIntent intent = internalIntent();
    long expectedNonce = intent.getUnsignedTxSnapshot().expectedNonce();
    when(executionIntentPersistencePort.claimNextInternalExecutableForUpdate(any()))
        .thenReturn(Optional.of(intent));
    when(executionTransactionGatewayPort.reserveNextNonce(SPONSOR_ADDRESS))
        .thenReturn(expectedNonce);
    when(executionTransactionGatewayPort.releaseReservedNonce(SPONSOR_ADDRESS, expectedNonce))
        .thenReturn(true);
    when(executionEip1559SigningPort.sign(any()))
        .thenThrow(new KmsSignFailedException("kms denied", terminalAwsKmsCause()));

    ExecuteInternalExecutionIntentResult result =
        delegate.execute(
            new ExecuteInternalExecutionIntentCommand(
                List.of(ExecutionActionType.QNA_ADMIN_SETTLE)),
            gate);

    assertThat(result.quarantined()).isTrue();
    assertThat(result.executionIntentStatus()).isEqualTo(ExecutionIntentStatus.CANCELED);
    verify(executionTransactionGatewayPort).releaseReservedNonce(SPONSOR_ADDRESS, expectedNonce);
    ArgumentCaptor<
            momzzangseven.mztkbe.modules.web3.execution.domain.event.ExecutionIntentTerminatedEvent>
        captor =
            ArgumentCaptor.forClass(
                momzzangseven.mztkbe.modules.web3.execution.domain.event
                    .ExecutionIntentTerminatedEvent.class);
    verify(publishExecutionIntentTerminatedPort).publish(captor.capture());
    assertThat(captor.getValue().failureReason()).isEqualTo(ErrorCode.WEB3_KMS_SIGN_FAILED.name());
    verify(executionTransactionGatewayPort, never()).createAndFlush(any());
    verify(executionTransactionGatewayPort, never()).broadcast(any());
  }

  @Test
  void execute_returnsTransientRetryWithExecutedFalseWhenKmsSignFailsTransiently() {
    ExecutionIntent intent = internalIntent();
    long expectedNonce = intent.getUnsignedTxSnapshot().expectedNonce();
    when(executionIntentPersistencePort.claimNextInternalExecutableForUpdate(any()))
        .thenReturn(Optional.of(intent));
    when(executionTransactionGatewayPort.reserveNextNonce(SPONSOR_ADDRESS))
        .thenReturn(expectedNonce);
    when(executionTransactionGatewayPort.releaseReservedNonce(SPONSOR_ADDRESS, expectedNonce))
        .thenReturn(true);
    // No AWS error code → classifier returns false → transient.
    when(executionEip1559SigningPort.sign(any()))
        .thenThrow(new KmsSignFailedException("kms throttled"));

    ExecuteInternalExecutionIntentResult result =
        delegate.execute(
            new ExecuteInternalExecutionIntentCommand(
                List.of(ExecutionActionType.QNA_ADMIN_SETTLE)),
            gate);

    // Critical: transient now reports executed=false so the batch loop exits the tick instead
    // of re-claiming the same intent and hammering KMS batchSize times.
    assertThat(result.executed()).isFalse();
    assertThat(result.quarantined()).isFalse();
    assertThat(result.executionIntentStatus()).isEqualTo(ExecutionIntentStatus.AWAITING_SIGNATURE);
    verify(executionTransactionGatewayPort).releaseReservedNonce(SPONSOR_ADDRESS, expectedNonce);
    // Critical invariant: transient path must NOT cancel intent and must NOT publish the
    // terminated event — the QnA escrow refund cascade must not fire on a recoverable hiccup.
    verify(executionIntentPersistencePort, never()).update(any());
    verify(publishExecutionIntentTerminatedPort, never()).publish(any());
    verify(executionTransactionGatewayPort, never()).createAndFlush(any());
    verify(executionTransactionGatewayPort, never()).broadcast(any());
  }

  @Test
  void execute_logsNonceGapButContinuesWhenReleaseReservedNonceReturnsFalse() {
    ExecutionIntent intent = internalIntent();
    long expectedNonce = intent.getUnsignedTxSnapshot().expectedNonce();
    when(executionIntentPersistencePort.claimNextInternalExecutableForUpdate(any()))
        .thenReturn(Optional.of(intent));
    when(executionTransactionGatewayPort.reserveNextNonce(SPONSOR_ADDRESS))
        .thenReturn(expectedNonce);
    when(executionTransactionGatewayPort.releaseReservedNonce(SPONSOR_ADDRESS, expectedNonce))
        .thenReturn(false); // simulate cursor advanced past abandoned nonce
    when(executionEip1559SigningPort.sign(any()))
        .thenThrow(new KmsSignFailedException("kms denied", terminalAwsKmsCause()));

    ExecuteInternalExecutionIntentResult result =
        delegate.execute(
            new ExecuteInternalExecutionIntentCommand(
                List.of(ExecutionActionType.QNA_ADMIN_SETTLE)),
            gate);

    // Gap is logged as ERROR but the terminal cancellation flow still completes.
    assertThat(result.quarantined()).isTrue();
    assertThat(result.executionIntentStatus()).isEqualTo(ExecutionIntentStatus.CANCELED);
    verify(executionTransactionGatewayPort).releaseReservedNonce(SPONSOR_ADDRESS, expectedNonce);
  }

  @Test
  void execute_quarantinesIntentWhenSignatureRecoveryFails_publishesWithSignatureRecoveryCode() {
    ExecutionIntent intent = internalIntent();
    long expectedNonce = intent.getUnsignedTxSnapshot().expectedNonce();
    when(executionIntentPersistencePort.claimNextInternalExecutableForUpdate(any()))
        .thenReturn(Optional.of(intent));
    when(executionTransactionGatewayPort.reserveNextNonce(SPONSOR_ADDRESS))
        .thenReturn(expectedNonce);
    when(executionTransactionGatewayPort.releaseReservedNonce(SPONSOR_ADDRESS, expectedNonce))
        .thenReturn(true);
    when(executionEip1559SigningPort.sign(any()))
        .thenThrow(new SignatureRecoveryException("recovery failed"));

    ExecuteInternalExecutionIntentResult result =
        delegate.execute(
            new ExecuteInternalExecutionIntentCommand(
                List.of(ExecutionActionType.QNA_ADMIN_SETTLE)),
            gate);

    assertThat(result.quarantined()).isTrue();
    assertThat(result.executionIntentStatus()).isEqualTo(ExecutionIntentStatus.CANCELED);
    verify(executionTransactionGatewayPort).releaseReservedNonce(SPONSOR_ADDRESS, expectedNonce);
    ArgumentCaptor<
            momzzangseven.mztkbe.modules.web3.execution.domain.event.ExecutionIntentTerminatedEvent>
        captor =
            ArgumentCaptor.forClass(
                momzzangseven.mztkbe.modules.web3.execution.domain.event
                    .ExecutionIntentTerminatedEvent.class);
    verify(publishExecutionIntentTerminatedPort).publish(captor.capture());
    assertThat(captor.getValue().failureReason())
        .isEqualTo(ErrorCode.WEB3_SIGNATURE_RECOVERY_FAILED.name());
    verify(executionTransactionGatewayPort, never()).createAndFlush(any());
  }

  @Test
  void execute_quarantinesIntentWhenSigningInputIsInvalid() {
    ExecutionIntent intent = internalIntent();
    long expectedNonce = intent.getUnsignedTxSnapshot().expectedNonce();
    when(executionIntentPersistencePort.claimNextInternalExecutableForUpdate(any()))
        .thenReturn(Optional.of(intent));
    when(executionTransactionGatewayPort.reserveNextNonce(SPONSOR_ADDRESS))
        .thenReturn(expectedNonce);
    when(executionTransactionGatewayPort.releaseReservedNonce(SPONSOR_ADDRESS, expectedNonce))
        .thenReturn(true);
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
    verify(executionTransactionGatewayPort).releaseReservedNonce(SPONSOR_ADDRESS, expectedNonce);
    verify(publishExecutionIntentTerminatedPort)
        .publish(
            org.mockito.ArgumentMatchers.argThat(
                event ->
                    event.executionIntentId().equals("intent-admin-settle")
                        && event.terminalStatus() == ExecutionIntentStatus.CANCELED));
    verify(executionTransactionGatewayPort, never()).createAndFlush(any());
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
}
