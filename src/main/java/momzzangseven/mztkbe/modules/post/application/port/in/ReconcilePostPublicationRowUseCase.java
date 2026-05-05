package momzzangseven.mztkbe.modules.post.application.port.in;

import momzzangseven.mztkbe.modules.post.application.dto.PostPublicationReconciliationRowResult;
import momzzangseven.mztkbe.modules.post.domain.model.Post;

public interface ReconcilePostPublicationRowUseCase {

  PostPublicationReconciliationRowResult reconcile(Post snapshot, boolean dryRun);
}
