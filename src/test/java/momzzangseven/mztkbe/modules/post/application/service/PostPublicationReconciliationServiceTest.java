package momzzangseven.mztkbe.modules.post.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.post.application.dto.RunPostPublicationReconciliationCommand;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadQuestionPublicationEvidencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionPublicationEvidence;
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
  @Mock private LoadQuestionPublicationEvidencePort loadQuestionPublicationEvidencePort;

  @InjectMocks private PostPublicationReconciliationService service;

  @Test
  @DisplayName("projection evidence marks question visible")
  void projectionEvidenceMarksVisible() {
    Post pending = questionPost(10L, PostPublicationStatus.PENDING);
    when(postPersistencePort.findQuestionPostsForPublicationReconciliation(null, 100))
        .thenReturn(List.of(pending));
    when(loadQuestionPublicationEvidencePort.loadEvidence(10L, 1L))
        .thenReturn(new QuestionPublicationEvidence(true, true, false, false, null));
    when(postPersistencePort.updateQuestionPublicationStatusIfCurrent(
            10L, PostPublicationStatus.PENDING, PostPublicationStatus.VISIBLE))
        .thenReturn(1);

    var result = service.run(new RunPostPublicationReconciliationCommand(null, null, false));

    verify(postPersistencePort)
        .updateQuestionPublicationStatusIfCurrent(
            10L, PostPublicationStatus.PENDING, PostPublicationStatus.VISIBLE);
    assertThat(result.changedToVisibleCount()).isEqualTo(1);
    assertThat(result.staleSkippedCount()).isZero();
  }

  @Test
  @DisplayName("missing projection without active create intent marks question failed")
  void missingProjectionWithoutActiveIntentMarksFailed() {
    Post pending = questionPost(11L, PostPublicationStatus.PENDING);
    when(postPersistencePort.findQuestionPostsForPublicationReconciliation(null, 10))
        .thenReturn(List.of(pending));
    when(loadQuestionPublicationEvidencePort.loadEvidence(11L, 1L))
        .thenReturn(new QuestionPublicationEvidence(true, false, false, true, "EXPIRED"));
    when(postPersistencePort.updateQuestionPublicationStatusIfCurrent(
            11L, PostPublicationStatus.PENDING, PostPublicationStatus.FAILED))
        .thenReturn(1);

    var result = service.run(new RunPostPublicationReconciliationCommand(null, 10, false));

    verify(postPersistencePort)
        .updateQuestionPublicationStatusIfCurrent(
            11L, PostPublicationStatus.PENDING, PostPublicationStatus.FAILED);
    assertThat(result.changedToFailedCount()).isEqualTo(1);
    assertThat(result.staleSkippedCount()).isZero();
  }

  @Test
  @DisplayName("visible question without projection is reported as needs-review without downgrade")
  void visibleQuestionWithoutProjectionNeedsReview() {
    Post visible = questionPost(13L, PostPublicationStatus.VISIBLE);
    when(postPersistencePort.findQuestionPostsForPublicationReconciliation(null, 10))
        .thenReturn(List.of(visible));
    when(loadQuestionPublicationEvidencePort.loadEvidence(13L, 1L))
        .thenReturn(new QuestionPublicationEvidence(true, false, false, false, null));

    var result = service.run(new RunPostPublicationReconciliationCommand(null, 10, false));

    assertThat(result.needsReviewCount()).isEqualTo(1);
    assertThat(result.staleSkippedCount()).isZero();
    verify(postPersistencePort, never())
        .updateQuestionPublicationStatusIfCurrent(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any(PostPublicationStatus.class),
            org.mockito.ArgumentMatchers.any(PostPublicationStatus.class));
  }

  @Test
  @DisplayName("dry run reports changes without saving")
  void dryRunDoesNotSave() {
    Post pending = questionPost(12L, PostPublicationStatus.PENDING);
    when(postPersistencePort.findQuestionPostsForPublicationReconciliation(9L, 1))
        .thenReturn(List.of(pending));
    when(loadQuestionPublicationEvidencePort.loadEvidence(12L, 1L))
        .thenReturn(new QuestionPublicationEvidence(false, false, false, false, null));

    var result = service.run(new RunPostPublicationReconciliationCommand(9L, 1, true));

    assertThat(result.changedToVisibleCount()).isEqualTo(1);
    assertThat(result.staleSkippedCount()).isZero();
    assertThat(result.lastScannedPostId()).isEqualTo(12L);
    verify(postPersistencePort, never()).savePost(org.mockito.ArgumentMatchers.any(Post.class));
    verify(postPersistencePort, never())
        .updateQuestionPublicationStatusIfCurrent(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any(PostPublicationStatus.class),
            org.mockito.ArgumentMatchers.any(PostPublicationStatus.class));
  }

  @Test
  @DisplayName("conditional update miss is reported as stale skipped without changed count")
  void staleConditionalUpdateIsSkippedWithoutChangedCount() {
    Post pending = questionPost(14L, PostPublicationStatus.PENDING);
    when(postPersistencePort.findQuestionPostsForPublicationReconciliation(null, 10))
        .thenReturn(List.of(pending));
    when(loadQuestionPublicationEvidencePort.loadEvidence(14L, 1L))
        .thenReturn(new QuestionPublicationEvidence(true, true, false, false, null));
    when(postPersistencePort.updateQuestionPublicationStatusIfCurrent(
            14L, PostPublicationStatus.PENDING, PostPublicationStatus.VISIBLE))
        .thenReturn(0);

    var result = service.run(new RunPostPublicationReconciliationCommand(null, 10, false));

    assertThat(result.changedToVisibleCount()).isZero();
    assertThat(result.changedToPendingCount()).isZero();
    assertThat(result.changedToFailedCount()).isZero();
    assertThat(result.staleSkippedCount()).isEqualTo(1);
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
