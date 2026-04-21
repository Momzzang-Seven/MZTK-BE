package momzzangseven.mztkbe.modules.post.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SyncQuestionAdminRefundStateService unit test")
class SyncQuestionAdminRefundStateServiceTest {

  @Mock private PostPersistencePort postPersistencePort;

  @InjectMocks private SyncQuestionAdminRefundStateService service;

  @Test
  @DisplayName("beginPendingRefund marks post as pending admin refund")
  void beginPendingRefund_marksPostPendingAdminRefund() {
    when(postPersistencePort.loadPostForUpdate(101L)).thenReturn(Optional.of(openQuestion(101L)));

    service.beginPendingRefund(101L);

    verify(postPersistencePort)
        .savePost(
            ArgumentMatchers.argThat(
                savedPost ->
                    savedPost.getId().equals(101L)
                        && savedPost.getStatus() == PostStatus.PENDING_ADMIN_REFUND
                        && savedPost.getAcceptedAnswerId() == null));
  }

  @Test
  @DisplayName("beginPendingRefund throws when post is missing")
  void beginPendingRefund_throwsWhenPostMissing() {
    when(postPersistencePort.loadPostForUpdate(101L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.beginPendingRefund(101L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("missing post");
  }

  @Test
  @DisplayName("rollbackPendingRefund reopens post")
  void rollbackPendingRefund_reopensPost() {
    when(postPersistencePort.loadPostForUpdate(101L))
        .thenReturn(Optional.of(pendingRefundQuestion(101L)));

    service.rollbackPendingRefund(101L);

    verify(postPersistencePort)
        .savePost(
            ArgumentMatchers.argThat(
                savedPost ->
                    savedPost.getId().equals(101L)
                        && savedPost.getStatus() == PostStatus.OPEN
                        && savedPost.getAcceptedAnswerId() == null));
  }

  private Post openQuestion(Long postId) {
    return Post.builder()
        .id(postId)
        .userId(1L)
        .type(PostType.QUESTION)
        .title("question")
        .content("content")
        .reward(10L)
        .status(PostStatus.OPEN)
        .createdAt(LocalDateTime.of(2026, 1, 1, 9, 0))
        .updatedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
        .build();
  }

  private Post pendingRefundQuestion(Long postId) {
    return Post.builder()
        .id(postId)
        .userId(1L)
        .type(PostType.QUESTION)
        .title("question")
        .content("content")
        .reward(10L)
        .status(PostStatus.PENDING_ADMIN_REFUND)
        .createdAt(LocalDateTime.of(2026, 1, 1, 9, 0))
        .updatedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
        .build();
  }
}
