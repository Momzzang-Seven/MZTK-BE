package momzzangseven.mztkbe.modules.web3.transaction.application.service;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.BulkRequeueWeb3TransactionItemResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.BulkRequeueWeb3TransactionsCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.BulkRequeueWeb3TransactionsResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.TransactionRequeueItemResultType;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.BulkRequeueWeb3TransactionsUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.reward-token", name = "enabled", havingValue = "true")
public class BulkRequeueWeb3TransactionsService implements BulkRequeueWeb3TransactionsUseCase {

  private final Web3TransactionRequeueProcessor processor;

  @Override
  @AdminOnly(
      actionType = "TRANSACTION_REQUEUE_BULK",
      targetType = AuditTargetType.WEB3_TRANSACTION,
      operatorId = "#command.operatorId()",
      targetId = "'BULK'",
      detail = {
        "requested=#command.requestedCount()",
        "unique=#command.uniqueTransactionIds().size()",
        "reason=#command.reason()",
        "evidence=#command.evidence()",
        "succeeded=#result != null ? #result.succeeded() : null",
        "failed=#result != null ? #result.failed() : null",
        "rejected=#result != null ? #result.rejected() : null",
        "notFound=#result != null ? #result.notFound() : null"
      })
  public BulkRequeueWeb3TransactionsResult execute(BulkRequeueWeb3TransactionsCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    command.validate();

    List<BulkRequeueWeb3TransactionItemResult> items = new ArrayList<>();
    int succeeded = 0;
    int failed = 0;
    int rejected = 0;
    int notFound = 0;

    for (Long transactionId : command.uniqueTransactionIds()) {
      BulkRequeueWeb3TransactionItemResult item =
          processor.requeueForBulk(
              command.operatorId(), transactionId, command.reason(), command.evidence());
      items.add(item);
      if (item.result() == TransactionRequeueItemResultType.REQUEUED) {
        succeeded++;
      } else if (item.result() == TransactionRequeueItemResultType.REJECTED) {
        rejected++;
      } else if (item.result() == TransactionRequeueItemResultType.NOT_FOUND) {
        notFound++;
      } else if (item.result() == TransactionRequeueItemResultType.FAILED) {
        failed++;
      }
    }

    return new BulkRequeueWeb3TransactionsResult(
        command.requestedCount(),
        command.uniqueTransactionIds().size(),
        succeeded,
        failed,
        rejected,
        notFound,
        items);
  }
}
