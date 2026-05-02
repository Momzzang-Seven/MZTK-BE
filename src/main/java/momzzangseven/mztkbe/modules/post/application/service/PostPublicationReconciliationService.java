package momzzangseven.mztkbe.modules.post.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.dto.RunPostPublicationReconciliationCommand;
import momzzangseven.mztkbe.modules.post.application.dto.RunPostPublicationReconciliationResult;
import momzzangseven.mztkbe.modules.post.application.port.in.RunPostPublicationReconciliationUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadQuestionPublicationEvidencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionPublicationEvidence;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostPublicationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostPublicationReconciliationService
    implements RunPostPublicationReconciliationUseCase {

  private final PostPersistencePort postPersistencePort;
  private final LoadQuestionPublicationEvidencePort loadQuestionPublicationEvidencePort;

  @Override
  @Transactional
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
      ReconciliationDecision decision = resolveDecision(post);
      if (decision.needsReview()) {
        needsReview++;
        continue;
      }
      PostPublicationStatus target = decision.targetStatus();
      if (post.getPublicationStatus() == target) {
        unchanged++;
        continue;
      }
      if (!command.dryRun()
          && !markPublicationStatusChanged(post.getId(), post.getPublicationStatus(), target)) {
        staleSkipped++;
        continue;
      }
      switch (target) {
        case PENDING -> pending++;
        case VISIBLE -> visible++;
        case FAILED -> failed++;
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

  private boolean markPublicationStatusChanged(
      Long postId, PostPublicationStatus currentStatus, PostPublicationStatus targetStatus) {
    int updatedRows =
        postPersistencePort.updateQuestionPublicationStatusIfCurrent(
            postId, currentStatus, targetStatus);
    if (updatedRows == 1) {
      return true;
    }
    if (updatedRows == 0) {
      return false;
    }
    throw new IllegalStateException(
        "Unexpected post publication status update row count: " + updatedRows);
  }

  private ReconciliationDecision resolveDecision(Post post) {
    QuestionPublicationEvidence evidence =
        loadQuestionPublicationEvidencePort.loadEvidence(post.getId(), post.getUserId());
    if (post.getPublicationStatus() == PostPublicationStatus.VISIBLE
        && evidence.lifecycleManaged()
        && !evidence.projectionExists()) {
      return ReconciliationDecision.needsReviewDecision();
    }
    if (!evidence.lifecycleManaged() || evidence.projectionExists()) {
      return ReconciliationDecision.target(PostPublicationStatus.VISIBLE);
    }
    if (evidence.activeCreateIntentExists()) {
      return ReconciliationDecision.target(PostPublicationStatus.PENDING);
    }
    return ReconciliationDecision.target(PostPublicationStatus.FAILED);
  }

  private record ReconciliationDecision(PostPublicationStatus targetStatus, boolean needsReview) {

    private static ReconciliationDecision target(PostPublicationStatus targetStatus) {
      return new ReconciliationDecision(targetStatus, false);
    }

    private static ReconciliationDecision needsReviewDecision() {
      return new ReconciliationDecision(null, true);
    }
  }
}
