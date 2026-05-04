package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import momzzangseven.mztkbe.global.error.web3.ExecutionIntentTerminalException;
import momzzangseven.mztkbe.global.error.web3.KmsSignFailedException;
import momzzangseven.mztkbe.global.error.web3.SignatureRecoveryException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentResult;
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
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.SponsorDailyUsage;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionReferenceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionRetryPolicy;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.kms.model.KmsException;

/**
 * Tests the per-mode signing logic that lives inside the @Transactional delegate. The sponsor
 * preflight is owned by {@link ExecuteExecutionIntentService}; the delegate receives a ready-made
 * {@link SponsorWalletGate} so these tests stub the gate directly without exercising preflight.
 */
@ExtendWith(MockitoExtension.class)
class TransactionalExecuteExecutionIntentDelegateTest {

  private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-07T03:00:00Z"), APP_ZONE);
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

  private TransactionalExecuteExecutionIntentDelegate delegate;

  @BeforeEach
  void setUp() {
    delegate =
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
            FIXED_CLOCK);
    lenient()
        .when(executionActionHandlerPort.supports(ExecutionActionType.TRANSFER_SEND))
        .thenReturn(true);
    lenient()
        .when(executionActionHandlerPort.buildActionPlan(org.mockito.ArgumentMatchers.any()))
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

  private SponsorWalletGate sponsorGate() {
    TreasuryWalletInfo info =
        new TreasuryWalletInfo(SPONSOR_ALIAS, SPONSOR_KMS_KEY_ID, SPONSOR_ADDRESS, true);
    TreasurySigner signer = new TreasurySigner(SPONSOR_ALIAS, SPONSOR_KMS_KEY_ID, SPONSOR_ADDRESS);
    return new SponsorWalletGate(info, signer);
  }

  @Test
  void execute_returnsExistingTransaction_whenIntentAlreadyHasSubmittedTxId() throws Exception {
    ExecutionIntent intent = existingEip1559Intent().toBuilder().submittedTxId(99L).build();
    ExecutionTransactionGatewayPort.TransactionRecord transaction =
        new ExecutionTransactionGatewayPort.TransactionRecord(
            99L, ExecutionTransactionStatus.SIGNED, "0xhash");

    when(executionIntentPersistencePort.findByPublicIdForUpdate("intent-1"))
        .thenReturn(Optional.of(intent));
    when(executionTransactionGatewayPort.findById(99L)).thenReturn(Optional.of(transaction));

    ExecuteExecutionIntentResult result =
        delegate.execute(
            new ExecuteExecutionIntentCommand(7L, "intent-1", null, null, null), sponsorGate());

    assertThat(result.transactionId()).isEqualTo(99L);
    assertThat(result.transactionStatus()).isEqualTo(ExecutionTransactionStatus.SIGNED);
    assertThat(result.txHash()).isEqualTo("0xhash");
  }

  @Test
  void execute_marksNonceStale_whenEip1559PendingNonceChanged() throws Exception {
    ExecutionIntent intent = existingEip1559Intent();
    Eip1559TransactionCodecPort.DecodedSignedTransaction decoded =
        new Eip1559TransactionCodecPort.DecodedSignedTransaction(
            "0xsigned",
            "0xhash",
            intent.getUnsignedTxSnapshot().fromAddress(),
            intent.getUnsignedTxSnapshot(),
            intent.getUnsignedTxFingerprint());

    when(executionIntentPersistencePort.findByPublicIdForUpdate("intent-1"))
        .thenReturn(Optional.of(intent));
    when(eip1559TransactionCodecPort.decodeAndVerify(
            "0xsigned", intent.getUnsignedTxSnapshot(), intent.getUnsignedTxFingerprint()))
        .thenReturn(decoded);
    when(executionEip7702GatewayPort.loadPendingAccountNonce(
            intent.getUnsignedTxSnapshot().fromAddress()))
        .thenReturn(BigInteger.valueOf(intent.getUnsignedTxSnapshot().expectedNonce() + 1));
    when(executionIntentPersistencePort.update(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    assertThatThrownBy(
            () ->
                delegate.execute(
                    new ExecuteExecutionIntentCommand(7L, "intent-1", null, null, "0xsigned"),
                    sponsorGate()))
        .isInstanceOf(ExecutionIntentTerminalException.class)
        .extracting(ex -> ((ExecutionIntentTerminalException) ex).getCode())
        .isEqualTo(ErrorCode.NONCE_STALE_RECREATE_REQUIRED.getCode());
  }

  @Test
  void executeEip1559_handlesBroadcastNullAuditFields_withoutThrowing() throws Exception {
    ExecutionIntent intent = existingEip1559Intent();
    Eip1559TransactionCodecPort.DecodedSignedTransaction decoded =
        new Eip1559TransactionCodecPort.DecodedSignedTransaction(
            "0xsigned",
            "0xhash",
            intent.getUnsignedTxSnapshot().fromAddress(),
            intent.getUnsignedTxSnapshot(),
            intent.getUnsignedTxFingerprint());
    ExecutionTransactionGatewayPort.TransactionRecord created =
        new ExecutionTransactionGatewayPort.TransactionRecord(
            101L, ExecutionTransactionStatus.CREATED, null);

    when(executionIntentPersistencePort.findByPublicIdForUpdate("intent-1"))
        .thenReturn(Optional.of(intent));
    when(eip1559TransactionCodecPort.decodeAndVerify(
            "0xsigned", intent.getUnsignedTxSnapshot(), intent.getUnsignedTxFingerprint()))
        .thenReturn(decoded);
    when(executionEip7702GatewayPort.loadPendingAccountNonce(
            intent.getUnsignedTxSnapshot().fromAddress()))
        .thenReturn(BigInteger.valueOf(intent.getUnsignedTxSnapshot().expectedNonce()));
    when(executionTransactionGatewayPort.createAndFlush(any())).thenReturn(created);
    when(executionTransactionGatewayPort.broadcast("0xsigned"))
        .thenReturn(new ExecutionTransactionGatewayPort.BroadcastResult(false, null, null, null));
    when(executionIntentPersistencePort.update(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ExecuteExecutionIntentResult result =
        delegate.execute(
            new ExecuteExecutionIntentCommand(7L, "intent-1", null, null, "0xsigned"),
            sponsorGate());

    assertThat(result.executionIntentStatus()).isEqualTo(ExecutionIntentStatus.SIGNED);
    assertThat(result.transactionId()).isEqualTo(101L);
    assertThat(result.transactionStatus()).isEqualTo(ExecutionTransactionStatus.SIGNED);
    assertThat(result.txHash()).isEqualTo("0xhash");
  }

  @Test
  void execute_marksExpired_whenIntentExpiredRelativeToInjectedClock() throws Exception {
    ExecutionIntent intent =
        existingEip1559Intent().toBuilder().expiresAt(FIXED_NOW.minusSeconds(1)).build();

    when(executionIntentPersistencePort.findByPublicIdForUpdate("intent-1"))
        .thenReturn(Optional.of(intent));
    when(executionIntentPersistencePort.update(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    assertThatThrownBy(
            () ->
                delegate.execute(
                    new ExecuteExecutionIntentCommand(7L, "intent-1", null, null, "0xsigned"),
                    sponsorGate()))
        .isInstanceOf(ExecutionIntentTerminalException.class)
        .extracting(ex -> ((ExecutionIntentTerminalException) ex).getCode())
        .isEqualTo(ErrorCode.EXECUTION_INTENT_EXPIRED.getCode());

    verify(executionIntentPersistencePort).update(any());
  }

  @Test
  void execute_marksNonceStale_whenEip7702AuthorityNonceChanged() throws Exception {
    ExecutionIntent intent = existingEip7702Intent();

    when(executionIntentPersistencePort.findByPublicIdForUpdate("intent-7702"))
        .thenReturn(Optional.of(intent));
    when(executionEip7702GatewayPort.toAuthorizationTuple(anyLong(), any(), any(), any()))
        .thenReturn(
            new ExecutionEip7702GatewayPort.AuthorizationTuple(
                BigInteger.valueOf(11155111L),
                "0x" + "2".repeat(40),
                BigInteger.valueOf(12L),
                BigInteger.ZERO,
                BigInteger.ONE,
                BigInteger.TWO));
    when(executionEip7702GatewayPort.hashCalls(any())).thenReturn("0x" + "9".repeat(64));
    when(executionEip7702GatewayPort.verifyAuthorizationSigner(
            anyLong(), any(), any(), any(), any()))
        .thenReturn(true);
    when(executionEip7702GatewayPort.loadPendingAccountNonce(intent.getAuthorityAddress()))
        .thenReturn(BigInteger.valueOf(intent.getAuthorityNonce() + 1));
    when(executionIntentPersistencePort.update(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    assertThatThrownBy(
            () ->
                delegate.execute(
                    new ExecuteExecutionIntentCommand(
                        7L, "intent-7702", "0xauth", "0xsubmit", null),
                    sponsorGate()))
        .isInstanceOf(ExecutionIntentTerminalException.class)
        .extracting(ex -> ((ExecutionIntentTerminalException) ex).getCode())
        .isEqualTo(ErrorCode.AUTH_NONCE_MISMATCH.getCode());

    verify(executionIntentPersistencePort).update(any());
  }

  private ExecutionIntent existingEip1559Intent() throws Exception {
    TransferExecutionPayload payload =
        new TransferExecutionPayload(
            "request-101",
            7L,
            8L,
            "0x" + "1".repeat(40),
            "0x" + "2".repeat(40),
            "0x" + "3".repeat(40),
            BigInteger.valueOf(100),
            "0x1234");

    return ExecutionIntent.create(
        "intent-1",
        "root-1",
        1,
        ExecutionResourceType.TRANSFER,
        "web3:TRANSFER_SEND:7:request-101",
        ExecutionActionType.TRANSFER_SEND,
        7L,
        8L,
        ExecutionMode.EIP1559,
        "0x" + "a".repeat(64),
        new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload),
        null,
        null,
        null,
        FIXED_NOW.plusMinutes(5),
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
        LocalDate.of(2026, 4, 6),
        FIXED_NOW);
  }

  private ExecutionIntent existingEip7702Intent() {
    return existingEip7702Intent(BigInteger.ZERO);
  }

  private ExecutionIntent existingEip7702Intent(BigInteger reservedSponsorCostWei) {
    return ExecutionIntent.create(
        "intent-7702",
        "root-7702",
        1,
        ExecutionResourceType.TRANSFER,
        "transfer:7702",
        ExecutionActionType.TRANSFER_SEND,
        7L,
        8L,
        ExecutionMode.EIP7702,
        "0x" + "a".repeat(64),
        "{\"amountWei\":\"100\"}",
        "0x" + "1".repeat(40),
        12L,
        "0x" + "2".repeat(40),
        FIXED_NOW.plusMinutes(5),
        "0x" + "3".repeat(64),
        "0x" + "4".repeat(64),
        null,
        null,
        reservedSponsorCostWei,
        LocalDate.of(2026, 4, 6),
        FIXED_NOW);
  }

  /**
   * Stub every port call up to {@code executionEip7702GatewayPort.signAndEncode(...)} so each KMS
   * test only needs to override the sign stub. {@code reserveNextNonce} returns the supplied
   * sponsor nonce so verification of {@code releaseReservedNonce} can match exactly.
   */
  private void stubEip7702HappyUntilSign(ExecutionIntent intent, long sponsorNonce) {
    when(executionIntentPersistencePort.findByPublicIdForUpdate(intent.getPublicId()))
        .thenReturn(Optional.of(intent));
    when(executionEip7702GatewayPort.toAuthorizationTuple(anyLong(), any(), any(), any()))
        .thenReturn(
            new ExecutionEip7702GatewayPort.AuthorizationTuple(
                BigInteger.valueOf(11155111L),
                "0x" + "2".repeat(40),
                BigInteger.valueOf(12L),
                BigInteger.ZERO,
                BigInteger.ONE,
                BigInteger.TWO));
    when(executionEip7702GatewayPort.hashCalls(any())).thenReturn("0x" + "9".repeat(64));
    when(executionEip7702GatewayPort.verifyAuthorizationSigner(
            anyLong(), any(), any(), any(), any()))
        .thenReturn(true);
    when(executionEip7702GatewayPort.loadPendingAccountNonce(intent.getAuthorityAddress()))
        .thenReturn(BigInteger.valueOf(intent.getAuthorityNonce()));
    when(executionEip7702GatewayPort.verifyExecutionSignature(any(), any(), any(), any(), any()))
        .thenReturn(true);
    when(executionEip7702GatewayPort.encodeExecute(any(), any())).thenReturn("0xexec");
    when(executionEip7702GatewayPort.estimateGasWithAuthorization(any(), any(), any(), any()))
        .thenReturn(BigInteger.valueOf(120_000));
    when(executionEip7702GatewayPort.loadSponsorFeePlan())
        .thenReturn(
            new ExecutionEip7702GatewayPort.FeePlan(
                BigInteger.valueOf(2_000_000_000L), BigInteger.valueOf(50_000_000_000L)));
    when(executionTransactionGatewayPort.reserveNextNonce(SPONSOR_ADDRESS))
        .thenReturn(sponsorNonce);
  }

  private static KmsException terminalAwsKmsCause() {
    return (KmsException)
        KmsException.builder()
            .awsErrorDetails(AwsErrorDetails.builder().errorCode("AccessDeniedException").build())
            .build();
  }

  @Test
  void executeEip7702_kmsTerminalError_cancelsIntentAndPublishesTerminatedEvent() {
    ExecutionIntent intent = existingEip7702Intent();
    long sponsorNonce = 42L;
    stubEip7702HappyUntilSign(intent, sponsorNonce);
    when(executionEip7702GatewayPort.signAndEncode(any()))
        .thenThrow(new KmsSignFailedException("kms denied", terminalAwsKmsCause()));
    when(executionIntentPersistencePort.update(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    assertThatThrownBy(
            () ->
                delegate.execute(
                    new ExecuteExecutionIntentCommand(
                        7L, "intent-7702", "0xauth", "0xsubmit", null),
                    sponsorGate()))
        .isInstanceOf(ExecutionIntentTerminalException.class)
        .extracting(ex -> ((ExecutionIntentTerminalException) ex).getCode())
        .isEqualTo(ErrorCode.WEB3_KMS_SIGN_FAILED.getCode());

    verify(executionIntentPersistencePort).update(any());
    verify(publishExecutionIntentTerminatedPort)
        .publish(
            org.mockito.ArgumentMatchers.argThat(
                event ->
                    event.executionIntentId().equals("intent-7702")
                        && event.terminalStatus() == ExecutionIntentStatus.CANCELED
                        && event.failureReason().equals(ErrorCode.WEB3_KMS_SIGN_FAILED.name())));
    verify(executionTransactionGatewayPort, never()).createAndFlush(any());
    verify(executionTransactionGatewayPort, never()).broadcast(any());
  }

  @Test
  void executeEip7702_kmsTerminalError_releasesReservedNonce() {
    ExecutionIntent intent = existingEip7702Intent();
    long sponsorNonce = 42L;
    stubEip7702HappyUntilSign(intent, sponsorNonce);
    when(executionEip7702GatewayPort.signAndEncode(any()))
        .thenThrow(new KmsSignFailedException("kms denied", terminalAwsKmsCause()));
    when(executionIntentPersistencePort.update(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    assertThatThrownBy(
            () ->
                delegate.execute(
                    new ExecuteExecutionIntentCommand(
                        7L, "intent-7702", "0xauth", "0xsubmit", null),
                    sponsorGate()))
        .isInstanceOf(ExecutionIntentTerminalException.class);

    verify(executionTransactionGatewayPort).releaseReservedNonce(SPONSOR_ADDRESS, sponsorNonce);
  }

  @Test
  void executeEip7702_kmsTerminalError_releasesSponsorExposureWhenReservedCostPositive() {
    BigInteger reservedCost = BigInteger.valueOf(12_345L);
    ExecutionIntent intent = existingEip7702Intent(reservedCost);
    long sponsorNonce = 42L;
    stubEip7702HappyUntilSign(intent, sponsorNonce);
    when(executionEip7702GatewayPort.signAndEncode(any()))
        .thenThrow(new KmsSignFailedException("kms denied", terminalAwsKmsCause()));
    when(executionIntentPersistencePort.update(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    SponsorDailyUsage usage = mock(SponsorDailyUsage.class);
    when(usage.release(any())).thenReturn(usage);
    when(sponsorDailyUsagePersistencePort.getOrCreateForUpdate(anyLong(), any())).thenReturn(usage);

    assertThatThrownBy(
            () ->
                delegate.execute(
                    new ExecuteExecutionIntentCommand(
                        7L, "intent-7702", "0xauth", "0xsubmit", null),
                    sponsorGate()))
        .isInstanceOf(ExecutionIntentTerminalException.class);

    verify(sponsorDailyUsagePersistencePort)
        .getOrCreateForUpdate(intent.getRequesterUserId(), intent.resolveSponsorUsageDateKst());
    verify(usage).release(reservedCost);
    verify(sponsorDailyUsagePersistencePort).update(usage);
  }

  @Test
  void executeEip7702_signatureRecoveryError_cancelsIntentAndPublishesTerminatedEvent() {
    ExecutionIntent intent = existingEip7702Intent();
    long sponsorNonce = 42L;
    stubEip7702HappyUntilSign(intent, sponsorNonce);
    when(executionEip7702GatewayPort.signAndEncode(any()))
        .thenThrow(new SignatureRecoveryException("v=27/28 mismatch"));
    when(executionIntentPersistencePort.update(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    assertThatThrownBy(
            () ->
                delegate.execute(
                    new ExecuteExecutionIntentCommand(
                        7L, "intent-7702", "0xauth", "0xsubmit", null),
                    sponsorGate()))
        .isInstanceOf(ExecutionIntentTerminalException.class)
        .extracting(ex -> ((ExecutionIntentTerminalException) ex).getCode())
        .isEqualTo(ErrorCode.WEB3_SIGNATURE_RECOVERY_FAILED.getCode());

    verify(executionTransactionGatewayPort).releaseReservedNonce(SPONSOR_ADDRESS, sponsorNonce);
    verify(publishExecutionIntentTerminatedPort)
        .publish(
            org.mockito.ArgumentMatchers.argThat(
                event ->
                    event.executionIntentId().equals("intent-7702")
                        && event.terminalStatus() == ExecutionIntentStatus.CANCELED
                        && event
                            .failureReason()
                            .equals(ErrorCode.WEB3_SIGNATURE_RECOVERY_FAILED.name())));
  }

  @Test
  void executeEip7702_kmsTransientError_leavesIntentInAwaitingSignature() {
    ExecutionIntent intent = existingEip7702Intent();
    long sponsorNonce = 42L;
    stubEip7702HappyUntilSign(intent, sponsorNonce);
    // No AWS cause → classifier returns non-terminal (transient).
    when(executionEip7702GatewayPort.signAndEncode(any()))
        .thenThrow(new KmsSignFailedException("kms throttled"));

    assertThatThrownBy(
            () ->
                delegate.execute(
                    new ExecuteExecutionIntentCommand(
                        7L, "intent-7702", "0xauth", "0xsubmit", null),
                    sponsorGate()))
        .isInstanceOf(KmsSignFailedException.class);

    // No cancel/intent state-change update should have been issued.
    verify(executionIntentPersistencePort, never()).update(any());
  }

  @Test
  void executeEip7702_kmsTransientError_doesNotPublishCascadeEvent() {
    ExecutionIntent intent = existingEip7702Intent();
    long sponsorNonce = 42L;
    stubEip7702HappyUntilSign(intent, sponsorNonce);
    when(executionEip7702GatewayPort.signAndEncode(any()))
        .thenThrow(new KmsSignFailedException("kms throttled"));

    assertThatThrownBy(
            () ->
                delegate.execute(
                    new ExecuteExecutionIntentCommand(
                        7L, "intent-7702", "0xauth", "0xsubmit", null),
                    sponsorGate()))
        .isInstanceOf(KmsSignFailedException.class);

    verifyNoInteractions(publishExecutionIntentTerminatedPort);
  }

  @Test
  void executeEip7702_kmsTransientError_rethrowsOriginalException() {
    ExecutionIntent intent = existingEip7702Intent();
    long sponsorNonce = 42L;
    stubEip7702HappyUntilSign(intent, sponsorNonce);
    KmsSignFailedException original = new KmsSignFailedException("kms throttled");
    when(executionEip7702GatewayPort.signAndEncode(any())).thenThrow(original);

    assertThatThrownBy(
            () ->
                delegate.execute(
                    new ExecuteExecutionIntentCommand(
                        7L, "intent-7702", "0xauth", "0xsubmit", null),
                    sponsorGate()))
        .isSameAs(original);
  }

  @Test
  void executeEip7702_kmsTransientError_callsReleaseReservedNonce() {
    ExecutionIntent intent = existingEip7702Intent();
    long sponsorNonce = 42L;
    stubEip7702HappyUntilSign(intent, sponsorNonce);
    when(executionEip7702GatewayPort.signAndEncode(any()))
        .thenThrow(new KmsSignFailedException("kms throttled"));

    assertThatThrownBy(
            () ->
                delegate.execute(
                    new ExecuteExecutionIntentCommand(
                        7L, "intent-7702", "0xauth", "0xsubmit", null),
                    sponsorGate()))
        .isInstanceOf(KmsSignFailedException.class);

    verify(executionTransactionGatewayPort).releaseReservedNonce(SPONSOR_ADDRESS, sponsorNonce);
  }
}
