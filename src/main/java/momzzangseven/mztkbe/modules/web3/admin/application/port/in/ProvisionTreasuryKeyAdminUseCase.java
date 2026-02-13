package momzzangseven.mztkbe.modules.web3.admin.application.port.in;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.ProvisionTreasuryKeyAdminCommand;
import momzzangseven.mztkbe.modules.web3.token.application.dto.ProvisionTreasuryKeyResult;

public interface ProvisionTreasuryKeyAdminUseCase {

  ProvisionTreasuryKeyResult execute(ProvisionTreasuryKeyAdminCommand command);
}
