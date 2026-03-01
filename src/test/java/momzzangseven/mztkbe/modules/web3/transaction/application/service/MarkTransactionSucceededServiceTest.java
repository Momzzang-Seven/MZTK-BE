package momzzangseven.mztkbe.modules.web3.transaction.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.MarkTransactionSucceededCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.MarkTransactionSucceededResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TransactionAuditEventType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarkTransactionSucceededServiceTest {

  @Mock private LoadTransactionPort loadTransactionPort;
  @Mock private TransactionOutcomePublisher transactionOutcomePublisher;
  @Mock private RecordTransactionAuditPort recordTransactionAuditPort;
  @Mock private Web3ContractPort web3ContractPort;

  private MarkTransactionSucceededService service;

  @BeforeEach
  void setUp() {
    service =
        new MarkTransactionSucceededService(
            loadTransactionPort,
            transactionOutcomePublisher,
            recordTransactionAuditPort,
            web3ContractPort);
  }

  @Test
  void execute_throws_whenCommandNull() {
    assertThatThrownBy(() -> service.execute(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("command is required");
  }

  @Test
  void execute_throws_whenCurrentStatusNotUnconfirmed() {
    MarkTransactionSucceededCommand command = validCommand();
    when(loadTransactionPort.loadById(22L))
        .thenReturn(
            Optional.of(
                new LoadTransactionPort.TransactionSnapshot(
                    22L,
                    "idem-22",
                    Web3ReferenceType.USER_TO_USER,
                    "101",
                    7L,
                    9L,
                    Web3TxStatus.PENDING,
                    "0x" + "a".repeat(64),
                    null)));

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(Web3TransactionStateInvalidException.class)
        .hasMessageContaining("only UNCONFIRMED");
  }

  @Test
  void execute_marksSucceeded_andRecordsAudit_whenReceiptProofValid() {
    MarkTransactionSucceededCommand command = validCommand();
    when(loadTransactionPort.loadById(22L))
        .thenReturn(
            Optional.of(
                new LoadTransactionPort.TransactionSnapshot(
                    22L,
                    "idem-22",
                    Web3ReferenceType.USER_TO_USER,
                    "101",
                    7L,
                    9L,
                    Web3TxStatus.UNCONFIRMED,
                    "0x" + "a".repeat(64),
                    null)));
    when(web3ContractPort.getReceipt("0x" + "a".repeat(64)))
        .thenReturn(
            new Web3ContractPort.ReceiptResult(
                "0x" + "a".repeat(64), true, true, "main", false, null));

    MarkTransactionSucceededResult result = service.execute(command);

    assertThat(result.transactionId()).isEqualTo(22L);
    assertThat(result.status()).isEqualTo(Web3TxStatus.SUCCEEDED);
    assertThat(result.previousStatus()).isEqualTo(Web3TxStatus.UNCONFIRMED);

    verify(transactionOutcomePublisher)
        .markSucceededAndPublish(
            22L, "idem-22", Web3ReferenceType.USER_TO_USER, "101", 7L, 9L, "0x" + "a".repeat(64));

    ArgumentCaptor<RecordTransactionAuditPort.AuditCommand> captor =
        ArgumentCaptor.forClass(RecordTransactionAuditPort.AuditCommand.class);
    verify(recordTransactionAuditPort).record(captor.capture());
    assertThat(captor.getValue().eventType()).isEqualTo(Web3TransactionAuditEventType.CS_OVERRIDE);
    assertThat(captor.getValue().detail()).containsEntry("toStatus", Web3TxStatus.SUCCEEDED.name());
  }

  private MarkTransactionSucceededCommand validCommand() {
    return new MarkTransactionSucceededCommand(
        1L, 22L, "0x" + "a".repeat(64), "https://explorer/tx/22", "manual proof", "ticket-22");
  }
}
