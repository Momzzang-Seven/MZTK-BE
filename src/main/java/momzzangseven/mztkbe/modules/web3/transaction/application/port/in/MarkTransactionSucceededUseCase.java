package momzzangseven.mztkbe.modules.web3.transaction.application.port.in;

import momzzangseven.mztkbe.modules.web3.transaction.application.command.MarkTransactionSucceededCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.result.MarkTransactionSucceededResult;

public interface MarkTransactionSucceededUseCase {

  MarkTransactionSucceededResult execute(MarkTransactionSucceededCommand command);
}
