package momzzangseven.mztkbe.modules.web3.admin.infrastructure.external.transaction.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.AdminWeb3TransactionView;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.BulkRequeueAdminWeb3TransactionItemResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.BulkRequeueAdminWeb3TransactionsCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.BulkRequeueAdminWeb3TransactionsResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetAdminWeb3TransactionsQuery;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.RequeueAdminWeb3TransactionCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.RequeueAdminWeb3TransactionResult;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.BulkRequeueWeb3TransactionsPort;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.LoadAdminWeb3TransactionsPort;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.RequeueWeb3TransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.BulkRequeueWeb3TransactionsCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.GetAdminWeb3TransactionsCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.RequeueWeb3TransactionCommand;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.reward-token", name = "enabled", havingValue = "true")
public class AdminWeb3TransactionRecoveryAdapter
    implements LoadAdminWeb3TransactionsPort,
        RequeueWeb3TransactionPort,
        BulkRequeueWeb3TransactionsPort {

  private final momzzangseven.mztkbe.modules.web3.transaction.application.port.in
          .GetAdminWeb3TransactionsUseCase
      getAdminWeb3TransactionsUseCase;

  private final momzzangseven.mztkbe.modules.web3.transaction.application.port.in
          .RequeueWeb3TransactionUseCase
      requeueWeb3TransactionUseCase;

  private final momzzangseven.mztkbe.modules.web3.transaction.application.port.in
          .BulkRequeueWeb3TransactionsUseCase
      bulkRequeueWeb3TransactionsUseCase;

  @Override
  public Page<AdminWeb3TransactionView> load(GetAdminWeb3TransactionsQuery query) {
    return getAdminWeb3TransactionsUseCase
        .execute(
            new GetAdminWeb3TransactionsCommand(
                query.operatorId(),
                query.failureReason(),
                query.status(),
                query.referenceType(),
                query.referenceId(),
                query.txType(),
                query.page(),
                query.size()))
        .map(
            result ->
                new AdminWeb3TransactionView(
                    result.transactionId(),
                    result.idempotencyKey(),
                    result.referenceType().name(),
                    result.referenceId(),
                    result.txType().name(),
                    result.fromUserId(),
                    result.toUserId(),
                    result.fromAddress(),
                    result.toAddress(),
                    result.status().name(),
                    result.txHash(),
                    result.failureReason(),
                    result.processingBy(),
                    result.processingUntil(),
                    result.signedAt(),
                    result.broadcastedAt(),
                    result.confirmedAt(),
                    result.createdAt(),
                    result.updatedAt()));
  }

  @Override
  public RequeueAdminWeb3TransactionResult requeue(RequeueAdminWeb3TransactionCommand command) {
    var result =
        requeueWeb3TransactionUseCase.execute(
            new RequeueWeb3TransactionCommand(
                command.operatorId(),
                command.transactionId(),
                command.reason(),
                command.evidence()));
    return new RequeueAdminWeb3TransactionResult(
        result.transactionId(),
        result.status().name(),
        result.previousStatus().name(),
        result.originalFailureReason(),
        result.requeued());
  }

  @Override
  public BulkRequeueAdminWeb3TransactionsResult requeue(
      BulkRequeueAdminWeb3TransactionsCommand command) {
    var result =
        bulkRequeueWeb3TransactionsUseCase.execute(
            new BulkRequeueWeb3TransactionsCommand(
                command.operatorId(),
                command.transactionIds(),
                command.reason(),
                command.evidence()));
    return new BulkRequeueAdminWeb3TransactionsResult(
        result.requested(),
        result.unique(),
        result.succeeded(),
        result.failed(),
        result.rejected(),
        result.notFound(),
        result.items().stream()
            .map(
                item ->
                    new BulkRequeueAdminWeb3TransactionItemResult(
                        item.transactionId(),
                        item.result().name(),
                        item.status() == null ? null : item.status().name(),
                        item.previousStatus() == null ? null : item.previousStatus().name(),
                        item.originalFailureReason(),
                        item.reason()))
            .toList());
  }
}
