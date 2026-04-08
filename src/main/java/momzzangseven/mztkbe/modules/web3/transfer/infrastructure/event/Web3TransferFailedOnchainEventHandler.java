package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.event;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.transaction.domain.event.Web3TransactionFailedOnchainEvent;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.HandleTransferFailedOnchainCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.HandleTransferFailedOnchainUseCase;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class Web3TransferFailedOnchainEventHandler {

  private final HandleTransferFailedOnchainUseCase handleTransferFailedOnchainUseCase;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(Web3TransactionFailedOnchainEvent event) {
    try {
      handleTransferFailedOnchainUseCase.execute(
          new HandleTransferFailedOnchainCommand(
              event.transactionId(),
              event.idempotencyKey(),
              event.referenceType().name(),
              event.referenceId(),
              event.fromUserId(),
              event.toUserId(),
              event.txHash(),
              event.failureReason()));
    } catch (Exception e) {
      log.error(
          "failed to handle web3 transfer failed-onchain event: txId={}, referenceId={}",
          event.transactionId(),
          event.referenceId(),
          e);
    }
  }
}
