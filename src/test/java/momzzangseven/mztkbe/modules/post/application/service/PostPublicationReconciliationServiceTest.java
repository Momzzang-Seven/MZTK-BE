package momzzangseven.mztkbe.modules.post.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.post.application.dto.PostPublicationReconciliationRowResult;
import momzzangseven.mztkbe.modules.post.application.dto.RunPostPublicationReconciliationCommand;
import momzzangseven.mztkbe.modules.post.application.port.in.ReconcilePostPublicationRowUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostPublicationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostPublicationReconciliationService unit test")
class PostPublicationReconciliationServiceTest {

  @Mock private PostPersistencePort postPersistencePort;
  @Mock private ReconcilePostPublicationRowUseCase reconcilePostPublicationRowUseCase;

  @InjectMocks private PostPublicationReconciliationService service;

  @Test
  @DisplayName("row changed to visible is counted")
  void rowChangedToVisibleIsCounted() {
    Post pending = questionPost(10L, PostPublicationStatus.PENDING);
    when(postPersistencePort.findQuestionPostsForPublicationReconciliation(null, 100))
        .thenReturn(List.of(pending));
    when(reconcilePostPublicationRowUseCase.reconcile(pending, false))
        .thenReturn(
            PostPublicationReconciliationRowResult.changed(10L, PostPublicationStatus.VISIBLE));

    var result = service.run(new RunPostPublicationReconciliationCommand(null, null, false));

    assertThat(result.changedToVisibleCount()).isEqualTo(1);
    assertThat(result.staleSkippedCount()).isZero();
  }

  @Test
  @DisplayName("row changed to failed is counted")
  void rowChangedToFailedIsCounted() {
    Post pending = questionPost(11L, PostPublicationStatus.PENDING);
    when(postPersistencePort.findQuestionPostsForPublicationReconciliation(null, 10))
        .thenReturn(List.of(pending));
    when(reconcilePostPublicationRowUseCase.reconcile(pending, false))
        .thenReturn(
            PostPublicationReconciliationRowResult.changed(11L, PostPublicationStatus.FAILED));

    var result = service.run(new RunPostPublicationReconciliationCommand(null, 10, false));

    assertThat(result.changedToFailedCount()).isEqualTo(1);
    assertThat(result.staleSkippedCount()).isZero();
  }

  @Test
  @DisplayName("needs-review row is counted without stopping batch")
  void needsReviewRowIsCounted() {
    Post visible = questionPost(13L, PostPublicationStatus.VISIBLE);
    when(postPersistencePort.findQuestionPostsForPublicationReconciliation(null, 10))
        .thenReturn(List.of(visible));
    when(reconcilePostPublicationRowUseCase.reconcile(visible, false))
        .thenReturn(PostPublicationReconciliationRowResult.needsReview(13L));

    var result = service.run(new RunPostPublicationReconciliationCommand(null, 10, false));

    assertThat(result.needsReviewCount()).isEqualTo(1);
    assertThat(result.staleSkippedCount()).isZero();
  }

  @Test
  @DisplayName("dry run reports changes without saving")
  void dryRunDoesNotSave() {
    Post pending = questionPost(12L, PostPublicationStatus.PENDING);
    when(postPersistencePort.findQuestionPostsForPublicationReconciliation(9L, 1))
        .thenReturn(List.of(pending));
    when(reconcilePostPublicationRowUseCase.reconcile(pending, true))
        .thenReturn(
            PostPublicationReconciliationRowResult.changed(12L, PostPublicationStatus.VISIBLE));

    var result = service.run(new RunPostPublicationReconciliationCommand(9L, 1, true));

    assertThat(result.changedToVisibleCount()).isEqualTo(1);
    assertThat(result.staleSkippedCount()).isZero();
    assertThat(result.lastScannedPostId()).isEqualTo(12L);
  }

  @Test
  @DisplayName("stale row is counted without changed count")
  void staleRowIsSkippedWithoutChangedCount() {
    Post pending = questionPost(14L, PostPublicationStatus.PENDING);
    when(postPersistencePort.findQuestionPostsForPublicationReconciliation(null, 10))
        .thenReturn(List.of(pending));
    when(reconcilePostPublicationRowUseCase.reconcile(pending, false))
        .thenReturn(PostPublicationReconciliationRowResult.staleSkipped(14L));

    var result = service.run(new RunPostPublicationReconciliationCommand(null, 10, false));

    assertThat(result.changedToVisibleCount()).isZero();
    assertThat(result.changedToPendingCount()).isZero();
    assertThat(result.changedToFailedCount()).isZero();
    assertThat(result.staleSkippedCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("row exception is isolated and counted as needs-review")
  void rowExceptionIsIsolated() {
    Post first = questionPost(14L, PostPublicationStatus.PENDING);
    Post second = questionPost(15L, PostPublicationStatus.PENDING);
    when(postPersistencePort.findQuestionPostsForPublicationReconciliation(null, 10))
        .thenReturn(List.of(first, second));
    when(reconcilePostPublicationRowUseCase.reconcile(first, false))
        .thenThrow(new IllegalStateException("row failed"));
    when(reconcilePostPublicationRowUseCase.reconcile(second, false))
        .thenReturn(
            PostPublicationReconciliationRowResult.changed(15L, PostPublicationStatus.VISIBLE));

    var result = service.run(new RunPostPublicationReconciliationCommand(null, 10, false));

    assertThat(result.needsReviewCount()).isEqualTo(1);
    assertThat(result.changedToVisibleCount()).isEqualTo(1);
    assertThat(result.lastScannedPostId()).isEqualTo(15L);
  }

  private Post questionPost(Long postId, PostPublicationStatus publicationStatus) {
    LocalDateTime now = LocalDateTime.of(2026, 1, 1, 10, 0);
    return Post.builder()
        .id(postId)
        .userId(1L)
        .type(PostType.QUESTION)
        .title("question")
        .content("content")
        .reward(100L)
        .status(PostStatus.OPEN)
        .publicationStatus(publicationStatus)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }
}
