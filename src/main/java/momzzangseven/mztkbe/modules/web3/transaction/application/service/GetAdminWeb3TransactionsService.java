package momzzangseven.mztkbe.modules.web3.transaction.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.AdminWeb3TransactionResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.GetAdminWeb3TransactionsCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.GetAdminWeb3TransactionsUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.ManageTransactionRecoveryPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.reward-token", name = "enabled", havingValue = "true")
public class GetAdminWeb3TransactionsService implements GetAdminWeb3TransactionsUseCase {

  private final ManageTransactionRecoveryPort manageTransactionRecoveryPort;

  @Override
  public Page<AdminWeb3TransactionResult> execute(GetAdminWeb3TransactionsCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    command.validate();

    return manageTransactionRecoveryPort
        .loadPage(
            new ManageTransactionRecoveryPort.RecoveryQuery(
                parseEnum(command.status(), Web3TxStatus.class, "status"),
                command.failureReason(),
                parseEnum(command.referenceType(), Web3ReferenceType.class, "referenceType"),
                command.referenceId(),
                parseEnum(command.txType(), Web3TxType.class, "txType"),
                command.normalizedPage(),
                command.normalizedSize()))
        .map(
            snapshot ->
                new AdminWeb3TransactionResult(
                    snapshot.transactionId(),
                    snapshot.idempotencyKey(),
                    snapshot.referenceType(),
                    snapshot.referenceId(),
                    snapshot.txType(),
                    snapshot.fromUserId(),
                    snapshot.toUserId(),
                    snapshot.fromAddress(),
                    snapshot.toAddress(),
                    snapshot.status(),
                    snapshot.txHash(),
                    snapshot.failureReason(),
                    snapshot.processingBy(),
                    snapshot.processingUntil(),
                    snapshot.signedAt(),
                    snapshot.broadcastedAt(),
                    snapshot.confirmedAt(),
                    snapshot.createdAt(),
                    snapshot.updatedAt()));
  }

  private static <E extends Enum<E>> E parseEnum(
      String value, Class<E> enumType, String fieldName) {
    if (value == null) {
      return null;
    }
    try {
      return Enum.valueOf(enumType, value);
    } catch (IllegalArgumentException e) {
      throw new Web3InvalidInputException(fieldName + " is invalid: " + value);
    }
  }
}
