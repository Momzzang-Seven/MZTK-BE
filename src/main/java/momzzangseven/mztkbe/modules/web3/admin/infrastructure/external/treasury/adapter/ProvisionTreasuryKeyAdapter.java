package momzzangseven.mztkbe.modules.web3.admin.infrastructure.external.treasury.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ProvisionTreasuryKeyResult;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ProvisionTreasuryKeyPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ProvisionTreasuryKeyCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.ProvisionTreasuryKeyUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryRole;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProvisionTreasuryKeyAdapter implements ProvisionTreasuryKeyPort {

  private final ProvisionTreasuryKeyUseCase provisionTreasuryKeyUseCase;

  @Override
  public ProvisionTreasuryKeyResult provision(
      Long operatorId, String rawPrivateKey, TreasuryRole role, String expectedAddress) {
    var result =
        provisionTreasuryKeyUseCase.execute(
            new ProvisionTreasuryKeyCommand(operatorId, rawPrivateKey, role, expectedAddress));
    return new ProvisionTreasuryKeyResult(
        result.walletAlias(),
        result.role(),
        result.kmsKeyId(),
        result.walletAddress(),
        result.status(),
        result.keyOrigin(),
        result.createdAt());
  }
}
