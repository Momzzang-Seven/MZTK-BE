package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.execution;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionSponsorWalletAddressUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminSignerAddressPort;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnAnyExecutionEnabled
public class LoadQnaAdminSignerAddressAdapter implements LoadQnaAdminSignerAddressPort {

  private final ObjectProvider<GetExecutionSponsorWalletAddressUseCase>
      getExecutionSponsorWalletAddressUseCaseProvider;

  @Override
  public String loadSignerAddress() {
    GetExecutionSponsorWalletAddressUseCase getExecutionSponsorWalletAddressUseCase =
        getExecutionSponsorWalletAddressUseCaseProvider.getIfAvailable();
    if (getExecutionSponsorWalletAddressUseCase == null) {
      throw new IllegalStateException(
          "QnA admin signer address is unavailable because internal issuer is disabled");
    }
    return getExecutionSponsorWalletAddressUseCase.execute();
  }
}
