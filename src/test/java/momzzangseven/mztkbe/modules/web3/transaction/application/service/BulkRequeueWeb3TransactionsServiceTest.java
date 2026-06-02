package momzzangseven.mztkbe.modules.web3.transaction.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.BulkRequeueWeb3TransactionItemResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.BulkRequeueWeb3TransactionsCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.BulkRequeueWeb3TransactionsResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.TransactionRequeueItemResultType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BulkRequeueWeb3TransactionsServiceTest {

  @Mock private Web3TransactionRequeueProcessor processor;

  private BulkRequeueWeb3TransactionsService service;

  @BeforeEach
  void setUp() {
    service = new BulkRequeueWeb3TransactionsService(processor);
  }

  @Test
  void execute_deduplicatesAndCountsEveryResultType() {
    when(processor.requeueForBulk(9L, 5L, "IAM restored", "ops-1234"))
        .thenReturn(
            new BulkRequeueWeb3TransactionItemResult(
                5L,
                TransactionRequeueItemResultType.REQUEUED,
                Web3TxStatus.CREATED,
                Web3TxStatus.CREATED,
                "KMS_DESCRIBE_TERMINAL",
                null));
    when(processor.requeueForBulk(9L, 6L, "IAM restored", "ops-1234"))
        .thenReturn(
            new BulkRequeueWeb3TransactionItemResult(
                6L,
                TransactionRequeueItemResultType.REJECTED,
                Web3TxStatus.UNCONFIRMED,
                Web3TxStatus.UNCONFIRMED,
                "RECEIPT_TIMEOUT_60S",
                "requeue requires CREATED status: current=UNCONFIRMED"));
    when(processor.requeueForBulk(9L, 7L, "IAM restored", "ops-1234"))
        .thenReturn(
            new BulkRequeueWeb3TransactionItemResult(
                7L, TransactionRequeueItemResultType.NOT_FOUND, null, null, null, "not found"));
    when(processor.requeueForBulk(9L, 8L, "IAM restored", "ops-1234"))
        .thenReturn(
            new BulkRequeueWeb3TransactionItemResult(
                8L,
                TransactionRequeueItemResultType.FAILED,
                null,
                null,
                null,
                "RuntimeException: db down"));

    BulkRequeueWeb3TransactionsResult result =
        service.execute(
            new BulkRequeueWeb3TransactionsCommand(
                9L, List.of(8L, 7L, 6L, 5L, 5L), "IAM restored", "ops-1234"));

    assertThat(result.requested()).isEqualTo(5);
    assertThat(result.unique()).isEqualTo(4);
    assertThat(result.succeeded()).isEqualTo(1);
    assertThat(result.rejected()).isEqualTo(1);
    assertThat(result.notFound()).isEqualTo(1);
    assertThat(result.failed()).isEqualTo(1);
    assertThat(result.items())
        .extracting(BulkRequeueWeb3TransactionItemResult::transactionId)
        .containsExactly(5L, 6L, 7L, 8L);
  }

  @Test
  void execute_rejectsRawRequestedCountOverLimitEvenWhenUniqueCountIsSmall() {
    BulkRequeueWeb3TransactionsCommand command =
        new BulkRequeueWeb3TransactionsCommand(
            9L,
            Collections.nCopies(BulkRequeueWeb3TransactionsCommand.MAX_TRANSACTION_IDS + 1, 5L),
            "IAM restored",
            "ops-1234");

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("transactionIds must not exceed");
    verifyNoInteractions(processor);
  }
}
