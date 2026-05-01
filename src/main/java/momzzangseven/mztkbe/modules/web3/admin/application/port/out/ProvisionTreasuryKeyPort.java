package momzzangseven.mztkbe.modules.web3.admin.application.port.out;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.ProvisionTreasuryKeyResult;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;

public interface ProvisionTreasuryKeyPort {

  ProvisionTreasuryKeyResult provision(
      Long operatorId, String rawPrivateKey, TreasuryRole role, String expectedAddress);
}
