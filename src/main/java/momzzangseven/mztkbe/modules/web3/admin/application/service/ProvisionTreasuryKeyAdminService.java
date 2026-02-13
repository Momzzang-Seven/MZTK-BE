package momzzangseven.mztkbe.modules.web3.admin.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ProvisionTreasuryKeyAdminCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.ProvisionTreasuryKeyAdminUseCase;
import momzzangseven.mztkbe.modules.web3.token.application.dto.ProvisionTreasuryKeyResult;
import momzzangseven.mztkbe.modules.web3.token.application.port.in.ProvisionTreasuryKeyUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3.reward-token.treasury.provisioning",
    name = "enabled",
    havingValue = "true")
public class ProvisionTreasuryKeyAdminService implements ProvisionTreasuryKeyAdminUseCase {

  private final ProvisionTreasuryKeyUseCase provisionTreasuryKeyUseCase;

  @Override
  public ProvisionTreasuryKeyResult execute(ProvisionTreasuryKeyAdminCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    command.validate();
    return provisionTreasuryKeyUseCase.execute(command.operatorId(), command.treasuryPrivateKey());
  }
}
