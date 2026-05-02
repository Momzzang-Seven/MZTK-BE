package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletStateException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransferException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.Eip1559TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionEip7702GatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionTransactionGatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionChainIdPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionRetryPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadSponsorTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.VerifyTreasuryWalletForSignPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionReferenceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionRetryPolicy;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExecuteExecutionIntentServiceTest {

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
  @Mock private LoadSponsorTreasuryWalletPort loadSponsorTreasuryWalletPort;
  @Mock private VerifyTreasuryWalletForSignPort verifyTreasuryWalletForSignPort;
  @Mock private ExecutionEip7702GatewayPort executionEip7702GatewayPort;
  @Mock private Eip1559TransactionCodecPort eip1559TransactionCodecPort;
  @Mock private LoadExecutionChainIdPort loadExecutionChainIdPort;
  @Mock private LoadExecutionRetryPolicyPort loadExecutionRetryPolicyPort;
  @Mock private ExecutionActionHandlerPort executionActionHandlerPort;

  private ExecuteExecutionIntentService service;

  @BeforeEach
  void setUp() {
    service =
        new ExecuteExecutionIntentService(
            executionIntentPersistencePort,
            sponsorDailyUsagePersistencePort,
            executionTransactionGatewayPort,
            loadSponsorTreasuryWalletPort,
            verifyTreasuryWalletForSignPort,
            executionEip7702GatewayPort,
            eip1559TransactionCodecPort,
            loadExecutionChainIdPort,
            loadExecutionRetryPolicyPort,
            List.of(executionActionHandlerPort),
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

  private TreasuryWalletInfo activeSponsorWalletInfo() {
    return new TreasuryWalletInfo(SPONSOR_ALIAS, SPONSOR_KMS_KEY_ID, SPONSOR_ADDRESS, true);
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
        service.execute(new ExecuteExecutionIntentCommand(7L, "intent-1", null, null, null));

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
                service.execute(
                    new ExecuteExecutionIntentCommand(7L, "intent-1", null, null, "0xsigned")))
        .isInstanceOf(Web3TransferException.class)
        .extracting(ex -> ((Web3TransferException) ex).getCode())
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
        service.execute(new ExecuteExecutionIntentCommand(7L, "intent-1", null, null, "0xsigned"));

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
                service.execute(
                    new ExecuteExecutionIntentCommand(7L, "intent-1", null, null, "0xsigned")))
        .isInstanceOf(Web3TransferException.class)
        .extracting(ex -> ((Web3TransferException) ex).getCode())
        .isEqualTo(ErrorCode.EXECUTION_INTENT_EXPIRED.getCode());

    verify(executionIntentPersistencePort).update(any());
  }

  @Test
  void execute_marksNonceStale_whenEip7702AuthorityNonceChanged() throws Exception {
    ExecutionIntent intent = existingEip7702Intent();

    when(executionIntentPersistencePort.findByPublicIdForUpdate("intent-7702"))
        .thenReturn(Optional.of(intent));
    when(loadSponsorTreasuryWalletPort.load()).thenReturn(Optional.of(activeSponsorWalletInfo()));
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
                service.execute(
                    new ExecuteExecutionIntentCommand(
                        7L, "intent-7702", "0xauth", "0xsubmit", null)))
        .isInstanceOf(Web3TransferException.class)
        .extracting(ex -> ((Web3TransferException) ex).getCode())
        .isEqualTo(ErrorCode.AUTH_NONCE_MISMATCH.getCode());

    verify(executionIntentPersistencePort).update(any());
  }

  @Test
  void executeEip7702_throwsSponsorMissing_whenSponsorTreasuryWalletNotRegistered()
      throws Exception {
    ExecutionIntent intent = existingEip7702Intent();

    when(executionIntentPersistencePort.findByPublicIdForUpdate("intent-7702"))
        .thenReturn(Optional.of(intent));
    when(loadSponsorTreasuryWalletPort.load()).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.execute(
                    new ExecuteExecutionIntentCommand(
                        7L, "intent-7702", "0xauth", "0xsubmit", null)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessage("sponsor signer key is missing");
  }

  @Test
  void executeEip7702_throwsSponsorMissing_whenSponsorWalletNotActive() throws Exception {
    ExecutionIntent intent = existingEip7702Intent();

    when(executionIntentPersistencePort.findByPublicIdForUpdate("intent-7702"))
        .thenReturn(Optional.of(intent));
    when(loadSponsorTreasuryWalletPort.load())
        .thenReturn(
            Optional.of(
                new TreasuryWalletInfo(SPONSOR_ALIAS, SPONSOR_KMS_KEY_ID, SPONSOR_ADDRESS, false)));

    assertThatThrownBy(
            () ->
                service.execute(
                    new ExecuteExecutionIntentCommand(
                        7L, "intent-7702", "0xauth", "0xsubmit", null)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessage("sponsor signer key is missing");
  }

  @Test
  void executeEip7702_throwsSponsorMissing_whenKmsKeyIdBlank() throws Exception {
    ExecutionIntent intent = existingEip7702Intent();

    when(executionIntentPersistencePort.findByPublicIdForUpdate("intent-7702"))
        .thenReturn(Optional.of(intent));
    when(loadSponsorTreasuryWalletPort.load())
        .thenReturn(Optional.of(new TreasuryWalletInfo(SPONSOR_ALIAS, "", SPONSOR_ADDRESS, true)));

    assertThatThrownBy(
            () ->
                service.execute(
                    new ExecuteExecutionIntentCommand(
                        7L, "intent-7702", "0xauth", "0xsubmit", null)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessage("sponsor signer key is missing");
  }

  @Test
  void executeEip7702_throwsSponsorMissing_whenWalletAddressBlank() throws Exception {
    ExecutionIntent intent = existingEip7702Intent();

    when(executionIntentPersistencePort.findByPublicIdForUpdate("intent-7702"))
        .thenReturn(Optional.of(intent));
    when(loadSponsorTreasuryWalletPort.load())
        .thenReturn(
            Optional.of(new TreasuryWalletInfo(SPONSOR_ALIAS, SPONSOR_KMS_KEY_ID, "", true)));

    assertThatThrownBy(
            () ->
                service.execute(
                    new ExecuteExecutionIntentCommand(
                        7L, "intent-7702", "0xauth", "0xsubmit", null)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessage("sponsor signer key is missing");
  }

  @Test
  void executeEip7702_propagates_whenVerifyForSignFails() throws Exception {
    ExecutionIntent intent = existingEip7702Intent();

    when(executionIntentPersistencePort.findByPublicIdForUpdate("intent-7702"))
        .thenReturn(Optional.of(intent));
    when(loadSponsorTreasuryWalletPort.load()).thenReturn(Optional.of(activeSponsorWalletInfo()));
    org.mockito.Mockito.doThrow(new TreasuryWalletStateException("KMS key disabled"))
        .when(verifyTreasuryWalletForSignPort)
        .verify(SPONSOR_ALIAS);

    assertThatThrownBy(
            () ->
                service.execute(
                    new ExecuteExecutionIntentCommand(
                        7L, "intent-7702", "0xauth", "0xsubmit", null)))
        .isInstanceOf(TreasuryWalletStateException.class)
        .hasMessageContaining("KMS key disabled");
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
        BigInteger.ZERO,
        LocalDate.of(2026, 4, 6),
        FIXED_NOW);
  }
}
