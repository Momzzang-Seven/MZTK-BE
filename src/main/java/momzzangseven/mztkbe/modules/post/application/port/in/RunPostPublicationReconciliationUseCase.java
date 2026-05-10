package momzzangseven.mztkbe.modules.post.application.port.in;

import momzzangseven.mztkbe.modules.post.application.dto.RunPostPublicationReconciliationCommand;
import momzzangseven.mztkbe.modules.post.application.dto.RunPostPublicationReconciliationResult;

public interface RunPostPublicationReconciliationUseCase {

  RunPostPublicationReconciliationResult run(RunPostPublicationReconciliationCommand command);
}
