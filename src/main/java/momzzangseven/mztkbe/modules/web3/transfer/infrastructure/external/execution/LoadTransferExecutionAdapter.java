package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.external.execution;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferSignRequestBundle;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.LoadTransferExecutionPort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionMode;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferTransactionStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(GetExecutionIntentUseCase.class)
@RequiredArgsConstructor
public class LoadTransferExecutionAdapter implements LoadTransferExecutionPort {

  private final GetExecutionIntentUseCase getExecutionIntentUseCase;

  @Override
  public TransferExecutionIntentResult load(Long requesterUserId, String executionIntentId) {
    GetExecutionIntentResult result =
        getExecutionIntentUseCase.execute(
            new GetExecutionIntentQuery(requesterUserId, executionIntentId));
    return new TransferExecutionIntentResult(
        TransferExecutionResourceType.valueOf(result.resourceType().name()),
        result.resourceId(),
        TransferExecutionResourceStatus.valueOf(result.resourceStatus().name()),
        result.executionIntentId(),
        TransferExecutionIntentStatus.valueOf(result.executionIntentStatus().name()),
        result.expiresAt(),
        TransferExecutionMode.valueOf(result.mode().name()),
        result.signCount(),
        toTransferSignRequest(result.signRequest()),
        false,
        result.transactionId(),
        result.transactionStatus() == null
            ? null
            : TransferTransactionStatus.valueOf(result.transactionStatus().name()),
        result.txHash());
  }

  private TransferSignRequestBundle toTransferSignRequest(SignRequestBundle signRequest) {
    if (signRequest == null) {
      return null;
    }
    if (signRequest.authorization() != null || signRequest.submit() != null) {
      return TransferSignRequestBundle.forEip7702(
          new TransferSignRequestBundle.AuthorizationSignRequest(
              signRequest.authorization().chainId(),
              signRequest.authorization().delegateTarget(),
              signRequest.authorization().authorityNonce(),
              signRequest.authorization().payloadHashToSign()),
          new TransferSignRequestBundle.SubmitSignRequest(
              signRequest.submit().executionDigest(), signRequest.submit().deadlineEpochSeconds()));
    }
    return TransferSignRequestBundle.forEip1559(
        new TransferSignRequestBundle.TransactionSignRequest(
            signRequest.transaction().chainId(),
            signRequest.transaction().fromAddress(),
            signRequest.transaction().toAddress(),
            signRequest.transaction().valueHex(),
            signRequest.transaction().data(),
            signRequest.transaction().nonce(),
            signRequest.transaction().gasLimitHex(),
            signRequest.transaction().maxPriorityFeePerGasHex(),
            signRequest.transaction().maxFeePerGasHex(),
            signRequest.transaction().expectedNonce()));
  }
}
