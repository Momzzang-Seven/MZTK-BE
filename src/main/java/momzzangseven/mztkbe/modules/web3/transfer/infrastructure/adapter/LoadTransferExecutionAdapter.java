package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferSignRequestBundle;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.LoadTransferExecutionPort;
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
        getExecutionIntentUseCase.execute(new GetExecutionIntentQuery(requesterUserId, executionIntentId));
    return new TransferExecutionIntentResult(
        result.resourceType().name(),
        result.resourceId(),
        result.resourceStatus().name(),
        result.executionIntentId(),
        result.executionIntentStatus().name(),
        result.expiresAt(),
        result.mode().name(),
        result.signCount(),
        toTransferSignRequest(result.signRequest()),
        false,
        result.transactionId(),
        result.transactionStatus(),
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
