package momzzangseven.mztkbe.modules.post.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.post.application.dto.PostPublicationReconciliationRowResult;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostPublicationReconciliationRowService unit test")
class PostPublicationReconciliationRowServiceTest {

  @Mock private PostPersistencePort postPersistencePort;
  @Mock private LoadQuestionPublicationEvidencePort loadQuestionPublicationEvidencePort;

  @InjectMocks private PostPublicationReconciliationRowService service;

  @Test
  @DisplayName("projection evidence marks pending question visible")
  void projectionEvidenceMarksVisible() {
    Post pending = questionPost(10L, PostPublicationStatus.PENDING);
    when(postPersistencePort.loadPostForUpdate(10L)).thenReturn(Optional.of(pending));
    when(loadQuestionPublicationEvidencePort.loadEvidence(10L, 1L))
        .thenReturn(
            new QuestionPublicationEvidence(true, true, "CREATED", false, false, null, "intent-1"));

    var result = service.reconcile(pending, false);

    ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
    verify(postPersistencePort).savePost(postCaptor.capture());
    assertThat(result.outcome()).isEqualTo(PostPublicationReconciliationRowResult.Outcome.CHANGED);
    assertThat(result.targetStatus()).isEqualTo(PostPublicationStatus.VISIBLE);
    assertThat(postCaptor.getValue().getPublicationStatus())
        .isEqualTo(PostPublicationStatus.VISIBLE);
    assertThat(postCaptor.getValue().getCurrentCreateExecutionIntentId()).isNull();
  }

  @Test
  @DisplayName("missing projection with terminal create evidence marks question failed")
  void terminalCreateEvidenceMarksFailed() {
    Post pending = questionPost(11L, PostPublicationStatus.PENDING);
    when(postPersistencePort.loadPostForUpdate(11L)).thenReturn(Optional.of(pending));
    when(loadQuestionPublicationEvidencePort.loadEvidence(11L, 1L))
        .thenReturn(
            new QuestionPublicationEvidence(true, false, null, false, true, "EXPIRED", "intent-1"));

    var result = service.reconcile(pending, false);

    ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
    verify(postPersistencePort).savePost(postCaptor.capture());
    assertThat(result.targetStatus()).isEqualTo(PostPublicationStatus.FAILED);
    assertThat(postCaptor.getValue().getPublicationStatus())
        .isEqualTo(PostPublicationStatus.FAILED);
    assertThat(postCaptor.getValue().getPublicationFailureTerminalStatus()).isEqualTo("EXPIRED");
  }

  @Test
  @DisplayName("active create evidence records current intent while keeping question pending")
  void activeCreateEvidenceRecordsCurrentIntent() {
    Post failed = questionPost(12L, PostPublicationStatus.FAILED);
    when(postPersistencePort.loadPostForUpdate(12L)).thenReturn(Optional.of(failed));
    when(loadQuestionPublicationEvidencePort.loadEvidence(12L, 1L))
        .thenReturn(
            new QuestionPublicationEvidence(true, false, null, true, false, "SIGNED", "intent-2"));

    var result = service.reconcile(failed, false);

    ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
    verify(postPersistencePort).savePost(postCaptor.capture());
    assertThat(result.targetStatus()).isEqualTo(PostPublicationStatus.PENDING);
    assertThat(postCaptor.getValue().getCurrentCreateExecutionIntentId()).isEqualTo("intent-2");
  }

  @Test
  @DisplayName("deleted projection never revives failed question")
  void deletedProjectionDoesNotReviveFailedQuestion() {
    Post failed = questionPost(13L, PostPublicationStatus.FAILED);
    when(postPersistencePort.loadPostForUpdate(13L)).thenReturn(Optional.of(failed));
    when(loadQuestionPublicationEvidencePort.loadEvidence(13L, 1L))
        .thenReturn(
            new QuestionPublicationEvidence(
                true, true, "DELETED_WITH_ANSWERS", false, true, "CONFIRMED", "intent-1"));

    var result = service.reconcile(failed, false);

    assertThat(result.outcome())
        .isEqualTo(PostPublicationReconciliationRowResult.Outcome.UNCHANGED);
    verify(postPersistencePort, never()).savePost(org.mockito.ArgumentMatchers.any(Post.class));
  }

  @Test
  @DisplayName("visible question with deleted projection requires manual review")
  void visibleDeletedProjectionNeedsReview() {
    Post visible = questionPost(14L, PostPublicationStatus.VISIBLE);
    when(postPersistencePort.loadPostForUpdate(14L)).thenReturn(Optional.of(visible));
    when(loadQuestionPublicationEvidencePort.loadEvidence(14L, 1L))
        .thenReturn(
            new QuestionPublicationEvidence(
                true, true, "DELETED", false, true, "CONFIRMED", "intent-1"));

    var result = service.reconcile(visible, false);

    assertThat(result.outcome())
        .isEqualTo(PostPublicationReconciliationRowResult.Outcome.NEEDS_REVIEW);
    verify(postPersistencePort, never()).savePost(org.mockito.ArgumentMatchers.any(Post.class));
  }

  @Test
  @DisplayName("fresh status mismatch is reported as stale skipped")
  void freshStatusMismatchIsStaleSkipped() {
    Post snapshot = questionPost(15L, PostPublicationStatus.PENDING);
    Post fresh = questionPost(15L, PostPublicationStatus.VISIBLE);
    when(postPersistencePort.loadPostForUpdate(15L)).thenReturn(Optional.of(fresh));

    var result = service.reconcile(snapshot, false);

    assertThat(result.outcome())
        .isEqualTo(PostPublicationReconciliationRowResult.Outcome.STALE_SKIPPED);
    verify(postPersistencePort, never()).savePost(org.mockito.ArgumentMatchers.any(Post.class));
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
