package momzzangseven.mztkbe.modules.web3.transaction.application.dto;

import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record BulkRequeueWeb3TransactionsResult(
    int requested,
    int unique,
    int succeeded,
    int failed,
    int rejected,
    int notFound,
    List<BulkRequeueWeb3TransactionItemResult> items) {

  public BulkRequeueWeb3TransactionsResult {
    if (requested < 0
        || unique < 0
        || succeeded < 0
        || failed < 0
        || rejected < 0
        || notFound < 0) {
      throw new Web3InvalidInputException("counts must be >= 0");
    }
    if (items == null) {
      throw new Web3InvalidInputException("items is required");
    }
    items = List.copyOf(items);
  }
}
