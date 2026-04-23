package momzzangseven.mztkbe.modules.web3.admin.infrastructure.external.transaction.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarkTransactionSucceededResult;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.MarkTransactionSucceededPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.reward-token", name = "enabled", havingValue = "true")
public class MarkTransactionSucceededAdapter implements MarkTransactionSucceededPort {

  private final momzzangseven.mztkbe.modules.web3.transaction.application.port.in
          .MarkTransactionSucceededUseCase
      markTransactionSucceededUseCase;

  @Override
  public MarkTransactionSucceededResult markSucceeded(
      Long operatorId,
      Long transactionId,
      String txHash,
      String explorerUrl,
      String reason,
      String evidence) {
    var result =
        markTransactionSucceededUseCase.execute(
            new momzzangseven.mztkbe.modules.web3.transaction.application.dto
                .MarkTransactionSucceededCommand(
                operatorId, transactionId, txHash, explorerUrl, reason, evidence));
    return new MarkTransactionSucceededResult(
        result.transactionId(),
        result.status().name(),
        result.previousStatus().name(),
        result.txHash(),
        result.explorerUrl());
  }
}
