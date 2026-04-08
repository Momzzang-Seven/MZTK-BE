package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import momzzangseven.mztkbe.global.error.web3.Web3TransferException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.Eip1559TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionEip7702GatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionTransactionGatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionChainIdPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionRetryPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionSponsorKeyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionSponsorWalletConfigPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionRetryPolicy;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionSponsorWalletConfig;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.TransferTransaction;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxType;
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

  @Mock private ExecutionIntentPersistencePort executionIntentPersistencePort;
  @Mock private SponsorDailyUsagePersistencePort sponsorDailyUsagePersistencePort;
  @Mock private ExecutionTransactionGatewayPort executionTransactionGatewayPort;
  @Mock private LoadExecutionSponsorKeyPort loadExecutionSponsorKeyPort;
  @Mock private ExecutionEip7702GatewayPort executionEip7702GatewayPort;
  @Mock private Eip1559TransactionCodecPort eip1559TransactionCodecPort;
  @Mock private LoadExecutionChainIdPort loadExecutionChainIdPort;
  @Mock private LoadExecutionSponsorWalletConfigPort loadExecutionSponsorWalletConfigPort;
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
            loadExecutionSponsorKeyPort,
            executionEip7702GatewayPort,
            eip1559TransactionCodecPort,
            loadExecutionChainIdPort,
            loadExecutionSponsorWalletConfigPort,
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
                Web3ReferenceType.USER_TO_USER,
                List.of(new ExecutionDraftCall("0x" + "3".repeat(40), BigInteger.ZERO, "0x1234"))));
    lenient()
        .when(loadExecutionRetryPolicyPort.loadRetryPolicy())
        .thenReturn(new ExecutionRetryPolicy(30));
    lenient().when(loadExecutionChainIdPort.loadChainId()).thenReturn(11155111L);
    lenient()
        .when(loadExecutionSponsorWalletConfigPort.loadSponsorWalletConfig())
        .thenReturn(new ExecutionSponsorWalletConfig("alias", "kek"));
  }

  @Test
  void execute_returnsExistingTransaction_whenIntentAlreadyHasSubmittedTxId() throws Exception {
    ExecutionIntent intent = existingEip1559Intent().toBuilder().submittedTxId(99L).build();
    TransferTransaction transaction =
        TransferTransaction.builder()
            .id(99L)
            .idempotencyKey("root-1:1")
            .referenceType(Web3ReferenceType.USER_TO_USER)
            .referenceId(intent.getResourceId())
            .fromUserId(7L)
            .toUserId(8L)
            .fromAddress("0x" + "1".repeat(40))
            .toAddress("0x" + "2".repeat(40))
            .amountWei(BigInteger.valueOf(100))
            .status(Web3TxStatus.SIGNED)
            .txType(Web3TxType.EIP1559)
            .txHash("0xhash")
            .build();

    when(executionIntentPersistencePort.findByPublicIdForUpdate("intent-1"))
        .thenReturn(Optional.of(intent));
    when(executionTransactionGatewayPort.findById(99L)).thenReturn(Optional.of(transaction));

    ExecuteExecutionIntentResult result =
        service.execute(new ExecuteExecutionIntentCommand(7L, "intent-1", null, null, null));

    assertThat(result.transactionId()).isEqualTo(99L);
    assertThat(result.transactionStatus()).isEqualTo(Web3TxStatus.SIGNED);
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

  private ExecutionIntent existingEip1559Intent() throws Exception {
    TransferExecutionPayload payload =
        new TransferExecutionPayload(
            "request-101",
            7L,
            8L,
            "0x" + "1".repeat(40),
            "0x" + "2".repeat(40),
            "0x" + "3".repeat(40),
            BigInteger.valueOf(100));

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
}
