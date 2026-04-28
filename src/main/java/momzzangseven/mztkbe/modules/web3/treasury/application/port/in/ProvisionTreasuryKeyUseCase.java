package momzzangseven.mztkbe.modules.web3.treasury.application.port.in;

import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ProvisionTreasuryKeyResult;

public interface ProvisionTreasuryKeyUseCase {

  ProvisionTreasuryKeyResult execute(Long operatorId, String walletAlias, String rawPrivateKey);
}
