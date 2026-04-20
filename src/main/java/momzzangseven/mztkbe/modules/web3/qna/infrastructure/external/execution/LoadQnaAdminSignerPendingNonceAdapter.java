package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.execution;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminSignerPendingNoncePort;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnInternalExecutionEnabled;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.ManageExecutionTransactionUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnInternalExecutionEnabled
public class LoadQnaAdminSignerPendingNonceAdapter implements LoadQnaAdminSignerPendingNoncePort {

  private final ManageExecutionTransactionUseCase manageExecutionTransactionUseCase;

  @Override
  public long loadPendingNonce(String signerAddress) {
    return manageExecutionTransactionUseCase.loadPendingNonce(signerAddress);
  }
}
