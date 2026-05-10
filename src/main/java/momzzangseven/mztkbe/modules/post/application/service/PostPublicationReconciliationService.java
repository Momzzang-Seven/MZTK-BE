package momzzangseven.mztkbe.modules.post.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.post.application.dto.PostPublicationReconciliationRowResult;
import momzzangseven.mztkbe.modules.post.application.dto.RunPostPublicationReconciliationCommand;
import momzzangseven.mztkbe.modules.post.application.dto.RunPostPublicationReconciliationResult;
import momzzangseven.mztkbe.modules.post.application.port.in.ReconcilePostPublicationRowUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.RunPostPublicationReconciliationUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostPublicationStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostPublicationReconciliationService
    implements RunPostPublicationReconciliationUseCase {

  private final PostPersistencePort postPersistencePort;
  private final ReconcilePostPublicationRowUseCase reconcilePostPublicationRowUseCase;

  @Override
  public RunPostPublicationReconciliationResult run(
      RunPostPublicationReconciliationCommand command) {
    int batchSize = command.effectiveBatchSize();
    List<Post> posts =
        postPersistencePort.findQuestionPostsForPublicationReconciliation(
            command.afterPostId(), batchSize);

    int unchanged = 0;
    int pending = 0;
    int visible = 0;
    int failed = 0;
    int needsReview = 0;
    int staleSkipped = 0;
    Long lastScannedPostId = null;

    for (Post post : posts) {
      lastScannedPostId = post.getId();
      PostPublicationReconciliationRowResult rowResult;
      try {
        rowResult = reconcilePostPublicationRowUseCase.reconcile(post, command.dryRun());
      } catch (RuntimeException e) {
        log.warn("failed to reconcile post publication row: postId={}", post.getId(), e);
        needsReview++;
        continue;
      }
      switch (rowResult.outcome()) {
        case UNCHANGED -> unchanged++;
        case NEEDS_REVIEW -> needsReview++;
        case STALE_SKIPPED -> staleSkipped++;
        case CHANGED -> {
          PostPublicationStatus target = rowResult.targetStatus();
          switch (target) {
            case PENDING -> pending++;
            case VISIBLE -> visible++;
            case FAILED -> failed++;
          }
        }
      }
    }

    return new RunPostPublicationReconciliationResult(
        posts.size(),
        unchanged,
        pending,
        visible,
        failed,
        needsReview,
        staleSkipped,
        lastScannedPostId,
        command.dryRun());
  }
}
