package momzzangseven.mztkbe.modules.post.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.dto.PostPublicationReconciliationRowResult;
import momzzangseven.mztkbe.modules.post.application.port.in.ReconcilePostPublicationRowUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadQuestionPublicationEvidencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionPublicationEvidence;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostPublicationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostPublicationReconciliationRowService implements ReconcilePostPublicationRowUseCase {

  private final PostPersistencePort postPersistencePort;
  private final LoadQuestionPublicationEvidencePort loadQuestionPublicationEvidencePort;

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public PostPublicationReconciliationRowResult reconcile(Post snapshot, boolean dryRun) {
    Post post = postPersistencePort.loadPostForUpdate(snapshot.getId()).orElse(null);
    if (post == null || post.getPublicationStatus() != snapshot.getPublicationStatus()) {
      return PostPublicationReconciliationRowResult.staleSkipped(snapshot.getId());
    }

    ReconciliationDecision decision = resolveDecision(post);
    if (decision.needsReview()) {
      return PostPublicationReconciliationRowResult.needsReview(post.getId());
    }

    PostPublicationStatus target = decision.targetStatus();
    if (post.getPublicationStatus() == target) {
      return PostPublicationReconciliationRowResult.unchanged(post.getId());
    }
    if (!dryRun) {
      postPersistencePort.savePost(applyTargetStatus(post, target, decision.evidence()));
    }
    return PostPublicationReconciliationRowResult.changed(post.getId(), target);
  }

  private Post applyTargetStatus(
      Post post, PostPublicationStatus target, QuestionPublicationEvidence evidence) {
    return switch (target) {
      case PENDING -> post.markPublicationPending(evidence.latestCreateExecutionIntentId());
      case VISIBLE -> post.markPublicationVisible();
      case FAILED ->
          post.markPublicationFailed(
              evidence.latestCreateIntentStatus(), "publication reconciliation");
    };
  }

  private ReconciliationDecision resolveDecision(Post post) {
    QuestionPublicationEvidence evidence =
        loadQuestionPublicationEvidencePort.loadEvidence(post.getId(), post.getUserId());
    if (!evidence.lifecycleManaged()) {
      return ReconciliationDecision.target(PostPublicationStatus.VISIBLE, evidence);
    }
    if (evidence.projectionDeleted()) {
      return post.getPublicationStatus() == PostPublicationStatus.VISIBLE
          ? ReconciliationDecision.needsReviewDecision()
          : ReconciliationDecision.target(PostPublicationStatus.FAILED, evidence);
    }
    if (evidence.projectionSupportsPublication()) {
      return ReconciliationDecision.target(PostPublicationStatus.VISIBLE, evidence);
    }
    if (post.getPublicationStatus() == PostPublicationStatus.VISIBLE) {
      return ReconciliationDecision.needsReviewDecision();
    }
    if (evidence.activeCreateIntentExists()) {
      return ReconciliationDecision.target(PostPublicationStatus.PENDING, evidence);
    }
    if (evidence.terminalCreateIntentExists()) {
      return ReconciliationDecision.target(PostPublicationStatus.FAILED, evidence);
    }
    return ReconciliationDecision.needsReviewDecision();
  }

  private record ReconciliationDecision(
      PostPublicationStatus targetStatus,
      boolean needsReview,
      QuestionPublicationEvidence evidence) {

    private static ReconciliationDecision target(
        PostPublicationStatus targetStatus, QuestionPublicationEvidence evidence) {
      return new ReconciliationDecision(targetStatus, false, evidence);
    }

    private static ReconciliationDecision needsReviewDecision() {
      return new ReconciliationDecision(null, true, null);
    }
  }
}
