package momzzangseven.mztkbe.modules.web3.admin.application.port.out;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.ProvisionTreasuryKeyResult;

public interface ProvisionTreasuryKeyPort {

  ProvisionTreasuryKeyResult provision(
      Long operatorId, String walletAlias, String treasuryPrivateKeyPlain);
}
