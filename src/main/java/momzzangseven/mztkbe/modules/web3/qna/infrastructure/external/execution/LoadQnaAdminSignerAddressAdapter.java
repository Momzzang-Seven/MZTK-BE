package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.execution;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionSponsorWalletAddressUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminSignerAddressPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class LoadQnaAdminSignerAddressAdapter implements LoadQnaAdminSignerAddressPort {

  private final GetExecutionSponsorWalletAddressUseCase getExecutionSponsorWalletAddressUseCase;

  @Override
  public String loadSignerAddress() {
    return getExecutionSponsorWalletAddressUseCase.execute();
  }
}
