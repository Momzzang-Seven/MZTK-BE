package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import java.util.List;
import lombok.Builder;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.BulkRequeueAdminWeb3TransactionItemResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.BulkRequeueAdminWeb3TransactionsResult;

@Builder
public record BulkRequeueWeb3TransactionsResponseDTO(
    int requested,
    int unique,
    int succeeded,
    int failed,
    int rejected,
    int notFound,
    List<Item> items) {

  public static BulkRequeueWeb3TransactionsResponseDTO from(
      BulkRequeueAdminWeb3TransactionsResult result) {
    if (result == null) {
      throw new Web3InvalidInputException("result is required");
    }
    return BulkRequeueWeb3TransactionsResponseDTO.builder()
        .requested(result.requested())
        .unique(result.unique())
        .succeeded(result.succeeded())
        .failed(result.failed())
        .rejected(result.rejected())
        .notFound(result.notFound())
        .items(result.items().stream().map(Item::from).toList())
        .build();
  }

  @Builder
  public record Item(
      Long transactionId,
      String result,
      String status,
      String previousStatus,
      String originalFailureReason,
      String reason) {

    private static Item from(BulkRequeueAdminWeb3TransactionItemResult result) {
      return Item.builder()
          .transactionId(result.transactionId())
          .result(result.result())
          .status(result.status())
          .previousStatus(result.previousStatus())
          .originalFailureReason(result.originalFailureReason())
          .reason(result.reason())
          .build();
    }
  }
}
