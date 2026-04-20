package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteInternalExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteInternalExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionEip1559SigningPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionTransactionGatewayPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionRetryPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionSponsorKeyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadInternalExecutionSignerConfigPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionReferenceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionRetryPolicy;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionSponsorWalletConfig;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExecuteInternalExecutionIntentServiceTest {

  private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-17T01:00:00Z"), APP_ZONE);
  private static final LocalDateTime FIXED_NOW =
      LocalDateTime.ofInstant(FIXED_CLOCK.instant(), APP_ZONE);

  @Mock private ExecutionIntentPersistencePort executionIntentPersistencePort;
  @Mock private ExecutionTransactionGatewayPort executionTransactionGatewayPort;
  @Mock private LoadInternalExecutionSignerConfigPort loadInternalExecutionSignerConfigPort;
  @Mock private LoadExecutionSponsorKeyPort loadExecutionSponsorKeyPort;
  @Mock private ExecutionEip1559SigningPort executionEip1559SigningPort;
  @Mock private LoadExecutionRetryPolicyPort loadExecutionRetryPolicyPort;
  @Mock private ExecutionActionHandlerPort executionActionHandlerPort;

  private ExecuteInternalExecutionIntentService service;

  @BeforeEach
  void setUp() {
    service =
        new ExecuteInternalExecutionIntentService(
            executionIntentPersistencePort,
            executionTransactionGatewayPort,
            loadInternalExecutionSignerConfigPort,
            loadExecutionSponsorKeyPort,
            executionEip1559SigningPort,
            loadExecutionRetryPolicyPort,
            List.of(executionActionHandlerPort),
            FIXED_CLOCK);

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
        .when(loadInternalExecutionSignerConfigPort.loadSignerConfig())
        .thenReturn(new ExecutionSponsorWalletConfig("alias", "kek"));
    lenient()
        .when(loadExecutionSponsorKeyPort.loadByAlias("alias", "kek"))
        .thenReturn(
            Optional.of(
                new LoadExecutionSponsorKeyPort.ExecutionSponsorKey(
                    "0x" + "4".repeat(40), "0x" + "9".repeat(64))));
    lenient()
        .when(loadExecutionRetryPolicyPort.loadRetryPolicy())
        .thenReturn(new ExecutionRetryPolicy(30));
    lenient()
        .when(executionIntentPersistencePort.update(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void execute_returnsNotFoundWhenNoEligibleIntentExists() {
    when(executionIntentPersistencePort.claimNextInternalExecutableForUpdate(any()))
        .thenReturn(Optional.empty());

    ExecuteInternalExecutionIntentResult result =
        service.execute(
            new ExecuteInternalExecutionIntentCommand(
                List.of(ExecutionActionType.QNA_ADMIN_SETTLE)));

    assertThat(result.executed()).isFalse();
    verify(executionActionHandlerPort, never()).beforeExecute(any(), any());
  }

  @Test
  void execute_marksNonceStaleWhenPendingNonceChanged() {
    ExecutionIntent intent = internalIntent();
    when(executionIntentPersistencePort.claimNextInternalExecutableForUpdate(any()))
        .thenReturn(Optional.of(intent));
    when(executionTransactionGatewayPort.loadPendingNonce("0x" + "4".repeat(40)))
        .thenReturn(intent.getUnsignedTxSnapshot().expectedNonce() + 1);

    ExecuteInternalExecutionIntentResult result =
        service.execute(
            new ExecuteInternalExecutionIntentCommand(
                List.of(ExecutionActionType.QNA_ADMIN_SETTLE)));

    assertThat(result.executed()).isTrue();
    assertThat(result.executionIntentStatus()).isEqualTo(ExecutionIntentStatus.NONCE_STALE);
    verify(executionActionHandlerPort).afterExecutionTerminated(any(), any(), any(), any());
  }

  @Test
  void execute_keepsNonceStaleTransitionWhenTerminationHookThrows() {
    ExecutionIntent intent = internalIntent();
    when(executionIntentPersistencePort.claimNextInternalExecutableForUpdate(any()))
        .thenReturn(Optional.of(intent));
    when(executionTransactionGatewayPort.loadPendingNonce("0x" + "4".repeat(40)))
        .thenReturn(intent.getUnsignedTxSnapshot().expectedNonce() + 1);
    doThrow(new IllegalStateException("rollback failed"))
        .when(executionActionHandlerPort)
        .afterExecutionTerminated(any(), any(), any(), any());

    ExecuteInternalExecutionIntentResult result =
        service.execute(
            new ExecuteInternalExecutionIntentCommand(
                List.of(ExecutionActionType.QNA_ADMIN_SETTLE)));

    assertThat(result.executed()).isTrue();
    assertThat(result.quarantined()).isFalse();
    assertThat(result.executionIntentStatus()).isEqualTo(ExecutionIntentStatus.NONCE_STALE);
    verify(executionIntentPersistencePort).update(any());
  }

  @Test
  void execute_marksPendingOnchainWhenBroadcastSucceeds() {
    ExecutionIntent intent = internalIntent();
    when(executionIntentPersistencePort.claimNextInternalExecutableForUpdate(any()))
        .thenReturn(Optional.of(intent));
    when(executionTransactionGatewayPort.loadPendingNonce("0x" + "4".repeat(40)))
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
        service.execute(
            new ExecuteInternalExecutionIntentCommand(
                List.of(ExecutionActionType.QNA_ADMIN_SETTLE)));

    assertThat(result.executed()).isTrue();
    assertThat(result.executionIntentStatus()).isEqualTo(ExecutionIntentStatus.PENDING_ONCHAIN);
    assertThat(result.transactionId()).isEqualTo(77L);
    assertThat(result.transactionStatus()).isEqualTo(ExecutionTransactionStatus.PENDING);
    assertThat(result.txHash()).isEqualTo("0xhash");
    verify(executionTransactionGatewayPort).markPending(77L, "0xhash");
  }

  @Test
  void execute_marksSignedAndSchedulesRetryWhenBroadcastFails() {
    ExecutionIntent intent = internalIntent();
    when(executionIntentPersistencePort.claimNextInternalExecutableForUpdate(any()))
        .thenReturn(Optional.of(intent));
    when(executionTransactionGatewayPort.loadPendingNonce("0x" + "4".repeat(40)))
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
        service.execute(
            new ExecuteInternalExecutionIntentCommand(
                List.of(ExecutionActionType.QNA_ADMIN_SETTLE)));

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
        service.execute(
            new ExecuteInternalExecutionIntentCommand(
                List.of(ExecutionActionType.QNA_ADMIN_SETTLE)));

    assertThat(result.executed()).isTrue();
    assertThat(result.quarantined()).isTrue();
    assertThat(result.executionIntentStatus()).isEqualTo(ExecutionIntentStatus.CANCELED);
    verify(executionActionHandlerPort).afterExecutionTerminated(any(), any(), any(), any());
    verify(executionTransactionGatewayPort, never()).loadPendingNonce(any());
    verify(executionEip1559SigningPort, never()).sign(any());
  }

  @Test
  void execute_keepsCanceledTransitionWhenTerminationHookThrowsDuringQuarantine() {
    ExecutionIntent intent = internalIntentWithSigner("0x" + "5".repeat(40));
    when(executionIntentPersistencePort.claimNextInternalExecutableForUpdate(any()))
        .thenReturn(Optional.of(intent));
    doThrow(new IllegalStateException("rollback failed"))
        .when(executionActionHandlerPort)
        .afterExecutionTerminated(any(), any(), any(), any());

    ExecuteInternalExecutionIntentResult result =
        service.execute(
            new ExecuteInternalExecutionIntentCommand(
                List.of(ExecutionActionType.QNA_ADMIN_SETTLE)));

    assertThat(result.executed()).isTrue();
    assertThat(result.quarantined()).isTrue();
    assertThat(result.executionIntentStatus()).isEqualTo(ExecutionIntentStatus.CANCELED);
    verify(executionIntentPersistencePort).update(any());
    verify(executionTransactionGatewayPort, never()).loadPendingNonce(any());
  }

  @Test
  void execute_quarantinesIntentWhenSponsorSignerAddressIsMalformed() {
    ExecutionIntent intent = internalIntent();
    when(executionIntentPersistencePort.claimNextInternalExecutableForUpdate(any()))
        .thenReturn(Optional.of(intent));
    when(loadExecutionSponsorKeyPort.loadByAlias("alias", "kek"))
        .thenReturn(
            Optional.of(
                new LoadExecutionSponsorKeyPort.ExecutionSponsorKey(
                    "not-an-address", "0x" + "9".repeat(64))));

    ExecuteInternalExecutionIntentResult result =
        service.execute(
            new ExecuteInternalExecutionIntentCommand(
                List.of(ExecutionActionType.QNA_ADMIN_SETTLE)));

    assertThat(result.executed()).isTrue();
    assertThat(result.quarantined()).isTrue();
    assertThat(result.executionIntentStatus()).isEqualTo(ExecutionIntentStatus.CANCELED);
    verify(executionActionHandlerPort).afterExecutionTerminated(any(), any(), any(), any());
    verify(executionTransactionGatewayPort, never()).loadPendingNonce(any());
    verify(executionEip1559SigningPort, never()).sign(any());
  }

  @Test
  void execute_quarantinesIntentWhenSigningInputIsInvalid() {
    ExecutionIntent intent = internalIntent();
    when(executionIntentPersistencePort.claimNextInternalExecutableForUpdate(any()))
        .thenReturn(Optional.of(intent));
    when(executionTransactionGatewayPort.loadPendingNonce("0x" + "4".repeat(40)))
        .thenReturn(intent.getUnsignedTxSnapshot().expectedNonce());
    when(executionEip1559SigningPort.sign(any()))
        .thenThrow(new Web3InvalidInputException("invalid EVM address: broken"));

    ExecuteInternalExecutionIntentResult result =
        service.execute(
            new ExecuteInternalExecutionIntentCommand(
                List.of(ExecutionActionType.QNA_ADMIN_SETTLE)));

    assertThat(result.executed()).isTrue();
    assertThat(result.quarantined()).isTrue();
    assertThat(result.executionIntentStatus()).isEqualTo(ExecutionIntentStatus.CANCELED);
    verify(executionActionHandlerPort).afterExecutionTerminated(any(), any(), any(), any());
    verify(executionTransactionGatewayPort, never()).createAndFlush(any());
    verify(executionTransactionGatewayPort, never()).broadcast(any());
  }

  private ExecutionIntent internalIntent() {
    return internalIntentWithSigner("0x" + "4".repeat(40));
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
