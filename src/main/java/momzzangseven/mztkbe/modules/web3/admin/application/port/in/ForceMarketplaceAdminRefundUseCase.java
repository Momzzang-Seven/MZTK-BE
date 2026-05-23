package momzzangseven.mztkbe.modules.web3.admin.application.port.in;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceMarketplaceAdminRefundCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceMarketplaceAdminRefundResult;

public interface ForceMarketplaceAdminRefundUseCase {

  ForceMarketplaceAdminRefundResult execute(ForceMarketplaceAdminRefundCommand command);
}
