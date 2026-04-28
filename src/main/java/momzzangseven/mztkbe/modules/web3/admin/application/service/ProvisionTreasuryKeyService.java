package momzzangseven.mztkbe.modules.web3.admin.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ProvisionTreasuryKeyCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ProvisionTreasuryKeyResult;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.ProvisionTreasuryKeyUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ProvisionTreasuryKeyPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service("web3AdminProvisionTreasuryKeyService")
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3.reward-token.treasury.provisioning",
    name = "enabled",
    havingValue = "true")
public class ProvisionTreasuryKeyService implements ProvisionTreasuryKeyUseCase {

  private final ProvisionTreasuryKeyPort provisionTreasuryKeyPort;

  @Override
  public ProvisionTreasuryKeyResult execute(ProvisionTreasuryKeyCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    command.validate();
    return provisionTreasuryKeyPort.provision(
        command.operatorId(),
        command.rawPrivateKey(),
        command.role(),
        command.expectedAddress());
  }
}
