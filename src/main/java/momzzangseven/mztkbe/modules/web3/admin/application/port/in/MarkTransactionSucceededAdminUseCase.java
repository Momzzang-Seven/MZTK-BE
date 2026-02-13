package momzzangseven.mztkbe.modules.web3.admin.application.port.in;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarkTransactionSucceededAdminCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.MarkTransactionSucceededResult;

public interface MarkTransactionSucceededAdminUseCase {

  MarkTransactionSucceededResult execute(MarkTransactionSucceededAdminCommand command);
}
