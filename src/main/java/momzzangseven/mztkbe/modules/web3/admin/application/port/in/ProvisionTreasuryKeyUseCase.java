package momzzangseven.mztkbe.modules.web3.admin.application.port.in;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.ProvisionTreasuryKeyCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ProvisionTreasuryKeyResult;

public interface ProvisionTreasuryKeyUseCase {

  ProvisionTreasuryKeyResult execute(ProvisionTreasuryKeyCommand command);
}
