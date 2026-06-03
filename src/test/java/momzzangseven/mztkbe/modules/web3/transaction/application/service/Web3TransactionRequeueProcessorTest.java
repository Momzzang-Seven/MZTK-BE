package momzzangseven.mztkbe.modules.web3.transaction.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.BulkRequeueWeb3TransactionItemResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.RequeueWeb3TransactionCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.RequeueWeb3TransactionResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.TransactionRequeueItemResultType;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadRewardTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.ManageTransactionRecoveryPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RunTransactionStateUpdatePort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TransactionAuditEventType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Web3TransactionRequeueProcessorTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-06-02T03:00:00Z"), ZoneId.of("Asia/Seoul"));

  @Mock private ManageTransactionRecoveryPort manageTransactionRecoveryPort;
  @Mock private LoadRewardTreasuryWalletPort loadRewardTreasuryWalletPort;
  @Mock private RecordTransactionAuditPort recordTransactionAuditPort;

  private final RunTransactionStateUpdatePort runTransactionStateUpdatePort =
      new RunTransactionStateUpdatePort() {
        @Override
        public <T> T requiresNew(java.util.function.Supplier<T> action) {
          return action.get();
        }
      };

  private Web3TransactionRequeueProcessor processor;

  @BeforeEach
  void setUp() {
    processor =
        new Web3TransactionRequeueProcessor(
            manageTransactionRecoveryPort,
            loadRewardTreasuryWalletPort,
            recordTransactionAuditPort,
            runTransactionStateUpdatePort,
            FIXED_CLOCK);
  }

  @Test
  void requeueOrThrow_returnsSuccessAndRecordsAudit() {
    when(manageTransactionRecoveryPort.loadByIdForUpdate(5L))
        .thenReturn(
            Optional.of(recoverySnapshot(5L, Web3TxFailureReason.KMS_DESCRIBE_TERMINAL.code())));
    when(manageTransactionRecoveryPort.clearFailureForRequeue(5L))
        .thenReturn(
            new ManageTransactionRecoveryPort.RequeueMutation(
                5L, Web3TxStatus.CREATED, Web3TxStatus.CREATED, "KMS_DESCRIBE_TERMINAL"));

    RequeueWeb3TransactionResult result =
        processor.requeueOrThrow(
            new RequeueWeb3TransactionCommand(9L, 5L, "IAM restored", "ops-1234"));

    assertThat(result.transactionId()).isEqualTo(5L);
    assertThat(result.requeued()).isTrue();
    ArgumentCaptor<RecordTransactionAuditPort.AuditCommand> captor =
        ArgumentCaptor.forClass(RecordTransactionAuditPort.AuditCommand.class);
    verify(recordTransactionAuditPort).record(captor.capture());
    assertThat(captor.getValue().eventType()).isEqualTo(Web3TransactionAuditEventType.REQUEUE);
    assertThat(captor.getValue().detail()).containsEntry("result", "REQUEUED");
  }

  @Test
  void requeueOrThrow_rejectsWhenTreasurySignerStillMismatched() {
    when(manageTransactionRecoveryPort.loadByIdForUpdate(7L))
        .thenReturn(
            Optional.of(recoverySnapshot(7L, Web3TxFailureReason.FROM_ADDRESS_MISMATCH.code())));
    when(loadRewardTreasuryWalletPort.load())
        .thenReturn(
            Optional.of(
                new TreasuryWalletInfo("reward-treasury", "kms-1", "0x" + "c".repeat(40), true)));

    assertThatThrownBy(
            () ->
                processor.requeueOrThrow(
                    new RequeueWeb3TransactionCommand(9L, 7L, "wallet fixed", "ops-1")))
        .isInstanceOf(Web3TransactionStateInvalidException.class)
        .hasMessageContaining("treasury signer still mismatched");

    verify(manageTransactionRecoveryPort, never()).clearFailureForRequeue(7L);

    ArgumentCaptor<RecordTransactionAuditPort.AuditCommand> captor =
        ArgumentCaptor.forClass(RecordTransactionAuditPort.AuditCommand.class);
    verify(recordTransactionAuditPort).record(captor.capture());
    assertThat(captor.getValue().detail())
        .containsEntry("result", "REJECTED")
        .containsEntry("originalFailureReason", Web3TxFailureReason.FROM_ADDRESS_MISMATCH.code());
  }

  @Test
  void requeueOrThrow_keepsOriginalExceptionWhenRejectedAuditFails() {
    when(manageTransactionRecoveryPort.loadByIdForUpdate(70L))
        .thenReturn(
            Optional.of(recoverySnapshot(70L, Web3TxFailureReason.FROM_ADDRESS_MISMATCH.code())));
    when(loadRewardTreasuryWalletPort.load())
        .thenReturn(
            Optional.of(
                new TreasuryWalletInfo("reward-treasury", "kms-1", "0x" + "c".repeat(40), true)));
    doThrow(new RuntimeException("audit down")).when(recordTransactionAuditPort).record(any());

    assertThatThrownBy(
            () ->
                processor.requeueOrThrow(
                    new RequeueWeb3TransactionCommand(9L, 70L, "wallet fixed", "ops-70")))
        .isInstanceOf(Web3TransactionStateInvalidException.class)
        .hasMessageContaining("treasury signer still mismatched");
  }

  @Test
  void requeueForBulk_returnsNotFoundWithoutTransactionAudit() {
    when(manageTransactionRecoveryPort.loadByIdForUpdate(77L)).thenReturn(Optional.empty());

    BulkRequeueWeb3TransactionItemResult result =
        processor.requeueForBulk(9L, 77L, "IAM restored", "ops-1234");

    assertThat(result.result()).isEqualTo(TransactionRequeueItemResultType.NOT_FOUND);
    verify(recordTransactionAuditPort, never()).record(any());
  }

  @Test
  void requeueForBulk_recordsRejectedAuditWhenBusinessRuleFails() {
    when(manageTransactionRecoveryPort.loadByIdForUpdate(8L))
        .thenReturn(
            Optional.of(recoverySnapshot(8L, Web3TxFailureReason.INVALID_SIGNED_TX.code())));
    when(manageTransactionRecoveryPort.clearFailureForRequeue(8L))
        .thenThrow(
            new Web3TransactionStateInvalidException(
                "requeue failureReason is not allowlisted: current=INVALID_SIGNED_TX"));

    BulkRequeueWeb3TransactionItemResult result =
        processor.requeueForBulk(9L, 8L, "manual", "ops-8");

    assertThat(result.result()).isEqualTo(TransactionRequeueItemResultType.REJECTED);
    assertThat(result.reason()).contains("not allowlisted");

    ArgumentCaptor<RecordTransactionAuditPort.AuditCommand> captor =
        ArgumentCaptor.forClass(RecordTransactionAuditPort.AuditCommand.class);
    verify(recordTransactionAuditPort).record(captor.capture());
    assertThat(captor.getValue().detail())
        .containsEntry("result", "REJECTED")
        .containsEntry("originalFailureReason", Web3TxFailureReason.INVALID_SIGNED_TX.code());
  }

  @Test
  void requeueForBulk_returnsFailedWhenUnexpectedRuntimeExceptionOccurs() {
    when(manageTransactionRecoveryPort.loadByIdForUpdate(88L))
        .thenReturn(
            Optional.of(recoverySnapshot(88L, Web3TxFailureReason.KMS_DESCRIBE_TERMINAL.code())));
    when(manageTransactionRecoveryPort.clearFailureForRequeue(88L))
        .thenThrow(new RuntimeException("db down"));

    BulkRequeueWeb3TransactionItemResult result =
        processor.requeueForBulk(9L, 88L, "manual", "ops-88");

    assertThat(result.result()).isEqualTo(TransactionRequeueItemResultType.FAILED);
    assertThat(result.reason()).contains("RuntimeException: db down");
    verify(recordTransactionAuditPort, never()).record(any());
  }

  private ManageTransactionRecoveryPort.RecoverySnapshot recoverySnapshot(
      Long transactionId, String failureReason) {
    LocalDateTime now = LocalDateTime.ofInstant(FIXED_CLOCK.instant(), ZoneId.of("Asia/Seoul"));
    return new ManageTransactionRecoveryPort.RecoverySnapshot(
        transactionId,
        "idem-" + transactionId,
        Web3ReferenceType.LEVEL_UP_REWARD,
        "reward-" + transactionId,
        Web3TxType.EIP1559,
        null,
        7L,
        "0x" + "a".repeat(40),
        "0x" + "b".repeat(40),
        Web3TxStatus.CREATED,
        null,
        failureReason,
        null,
        null,
        null,
        null,
        null,
        null,
        now.plusMinutes(3),
        now.minusMinutes(1),
        now);
  }
}
