package momzzangseven.mztkbe.modules.web3.transaction.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.PrepareTokenTransferPrevalidationCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.PrepareTokenTransferPrevalidationResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.PrepareTokenTransferPrevalidationUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(prefix = "web3.reward-token", name = "enabled", havingValue = "true")
public class PrepareTokenTransferPrevalidationService
    implements PrepareTokenTransferPrevalidationUseCase {

  private final Web3ContractPort web3ContractPort;

  @Override
  public PrepareTokenTransferPrevalidationResult execute(
      PrepareTokenTransferPrevalidationCommand command) {
    Web3ContractPort.PrevalidateResult result =
        web3ContractPort.prevalidate(
            new Web3ContractPort.PrevalidateCommand(
                command.fromAddress(), command.toAddress(), command.amountWei()));
    return new PrepareTokenTransferPrevalidationResult(
        result.ok(),
        result.failureReason(),
        result.gasLimit(),
        result.maxPriorityFeePerGas(),
        result.maxFeePerGas());
  }
}
