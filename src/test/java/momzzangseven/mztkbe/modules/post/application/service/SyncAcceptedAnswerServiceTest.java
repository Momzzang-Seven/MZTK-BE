package momzzangseven.mztkbe.modules.post.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadAcceptedAnswerPort;
import momzzangseven.mztkbe.modules.post.application.port.out.MarkAcceptedAnswerPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SyncAcceptedAnswerService unit test")
class SyncAcceptedAnswerServiceTest {

  @Mock private PostPersistencePort postPersistencePort;
  @Mock private LoadAcceptedAnswerPort loadAcceptedAnswerPort;
  @Mock private MarkAcceptedAnswerPort markAcceptedAnswerPort;

  @InjectMocks private SyncAcceptedAnswerService syncAcceptedAnswerService;

  @Test
  @DisplayName("confirmAccepted loads answer before post and saves both states")
  void confirmAccepted_loadsAnswerBeforePost() {
    LoadAcceptedAnswerPort.AcceptedAnswerInfo answer = acceptedAnswer(201L, 101L);
    Post post = pendingPost(101L, 201L);
    when(loadAcceptedAnswerPort.loadAcceptedAnswerForUpdate(201L)).thenReturn(Optional.of(answer));
    when(postPersistencePort.loadPostForUpdate(101L)).thenReturn(Optional.of(post));

    syncAcceptedAnswerService.confirmAccepted(101L, 201L);

    var inOrder = inOrder(loadAcceptedAnswerPort, postPersistencePort, markAcceptedAnswerPort);
    inOrder.verify(loadAcceptedAnswerPort).loadAcceptedAnswerForUpdate(201L);
    inOrder.verify(postPersistencePort).loadPostForUpdate(101L);
    inOrder
        .verify(postPersistencePort)
        .savePost(
            org.mockito.ArgumentMatchers.argThat(
                savedPost ->
                    savedPost.getId().equals(101L)
                        && savedPost.getStatus() == PostStatus.RESOLVED
                        && savedPost.getAcceptedAnswerId().equals(201L)));
    inOrder.verify(markAcceptedAnswerPort).markAccepted(201L);
  }

  @Test
  @DisplayName("confirmAccepted throws when answer belongs to different post")
  void confirmAccepted_throwsWhenAnswerPostMismatch() {
    when(loadAcceptedAnswerPort.loadAcceptedAnswerForUpdate(201L))
        .thenReturn(Optional.of(acceptedAnswer(201L, 999L)));
    when(postPersistencePort.loadPostForUpdate(101L))
        .thenReturn(Optional.of(pendingPost(101L, 201L)));

    assertThatThrownBy(() -> syncAcceptedAnswerService.confirmAccepted(101L, 201L))
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("does not belong");

    verify(markAcceptedAnswerPort, never()).markAccepted(any());
  }

  @Test
  @DisplayName("confirmAccepted throws when answer is missing")
  void confirmAccepted_throwsWhenAnswerMissing() {
    when(loadAcceptedAnswerPort.loadAcceptedAnswerForUpdate(201L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> syncAcceptedAnswerService.confirmAccepted(101L, 201L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("missing answer");
  }

  @Test
  @DisplayName("confirmAccepted throws when post is missing")
  void confirmAccepted_throwsWhenPostMissing() {
    when(loadAcceptedAnswerPort.loadAcceptedAnswerForUpdate(201L))
        .thenReturn(Optional.of(acceptedAnswer(201L, 101L)));
    when(postPersistencePort.loadPostForUpdate(101L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> syncAcceptedAnswerService.confirmAccepted(101L, 201L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("missing post");
    verify(markAcceptedAnswerPort, never()).markAccepted(any());
  }

  @Test
  @DisplayName("rollbackPendingAccept loads answer before post and reopens post")
  void rollbackPendingAccept_loadsAnswerBeforePost() {
    when(loadAcceptedAnswerPort.loadAcceptedAnswerForUpdate(201L))
        .thenReturn(Optional.of(acceptedAnswer(201L, 101L)));
    when(postPersistencePort.loadPostForUpdate(101L))
        .thenReturn(Optional.of(pendingPost(101L, 201L)));

    syncAcceptedAnswerService.rollbackPendingAccept(101L, 201L);

    var inOrder = inOrder(loadAcceptedAnswerPort, postPersistencePort);
    inOrder.verify(loadAcceptedAnswerPort).loadAcceptedAnswerForUpdate(201L);
    inOrder.verify(postPersistencePort).loadPostForUpdate(101L);
    inOrder
        .verify(postPersistencePort)
        .savePost(
            org.mockito.ArgumentMatchers.argThat(
                savedPost ->
                    savedPost.getId().equals(101L)
                        && savedPost.getStatus() == PostStatus.OPEN
                        && savedPost.getAcceptedAnswerId() == null));
    verifyNoInteractions(markAcceptedAnswerPort);
  }

  @Test
  @DisplayName("rollbackPendingAccept throws when answer belongs to different post")
  void rollbackPendingAccept_throwsWhenAnswerPostMismatch() {
    when(loadAcceptedAnswerPort.loadAcceptedAnswerForUpdate(201L))
        .thenReturn(Optional.of(acceptedAnswer(201L, 999L)));
    when(postPersistencePort.loadPostForUpdate(101L))
        .thenReturn(Optional.of(pendingPost(101L, 201L)));

    assertThatThrownBy(() -> syncAcceptedAnswerService.rollbackPendingAccept(101L, 201L))
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("does not belong");

    verifyNoInteractions(markAcceptedAnswerPort);
  }

  @Test
  @DisplayName("rollbackPendingAccept throws when answer is missing")
  void rollbackPendingAccept_throwsWhenAnswerMissing() {
    when(loadAcceptedAnswerPort.loadAcceptedAnswerForUpdate(201L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> syncAcceptedAnswerService.rollbackPendingAccept(101L, 201L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("missing answer");
  }

  private LoadAcceptedAnswerPort.AcceptedAnswerInfo acceptedAnswer(Long answerId, Long postId) {
    return new LoadAcceptedAnswerPort.AcceptedAnswerInfo(answerId, postId, 2L, "answer");
  }

  private Post pendingPost(Long postId, Long answerId) {
    return Post.builder()
        .id(postId)
        .userId(1L)
        .type(PostType.QUESTION)
        .title("question")
        .content("content")
        .reward(10L)
        .acceptedAnswerId(answerId)
        .status(PostStatus.PENDING_ACCEPT)
        .createdAt(LocalDateTime.of(2026, 1, 1, 9, 0))
        .updatedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
        .build();
  }
}
