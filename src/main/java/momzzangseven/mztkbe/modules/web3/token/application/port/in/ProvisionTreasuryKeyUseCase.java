package momzzangseven.mztkbe.modules.web3.token.application.port.in;

import momzzangseven.mztkbe.modules.web3.token.api.dto.ProvisionTreasuryKeyResponseDTO;

public interface ProvisionTreasuryKeyUseCase {

  ProvisionTreasuryKeyResponseDTO execute(String rawPrivateKey);
}
