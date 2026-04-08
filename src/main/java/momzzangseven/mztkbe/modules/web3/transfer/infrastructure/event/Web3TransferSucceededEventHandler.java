package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.transaction.domain.event.Web3TransactionSucceededEvent;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.HandleTransferSucceededCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.HandleTransferSucceededUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferTransactionReferenceType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class Web3TransferSucceededEventHandler {

  private final HandleTransferSucceededUseCase handleTransferSucceededUseCase;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(Web3TransactionSucceededEvent event) {
    try {
      handleTransferSucceededUseCase.execute(
          new HandleTransferSucceededCommand(
              event.transactionId(),
              event.idempotencyKey(),
              TransferTransactionReferenceType.valueOf(event.referenceType().name()),
              event.referenceId(),
              event.fromUserId(),
              event.toUserId(),
              event.txHash()));
    } catch (Exception e) {
      log.error(
          "failed to handle web3 transfer success event: txId={}, referenceId={}",
          event.transactionId(),
          event.referenceId(),
          e);
    }
  }
}
