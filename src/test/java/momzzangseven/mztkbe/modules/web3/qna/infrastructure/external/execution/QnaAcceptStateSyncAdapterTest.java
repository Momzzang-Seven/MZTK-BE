package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.execution;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.SaveAnswerPort;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("QnaAcceptStateSyncAdapter unit test")
class QnaAcceptStateSyncAdapterTest {

  @Mock private PostPersistencePort postPersistencePort;
  @Mock private LoadAnswerPort loadAnswerPort;
  @Mock private SaveAnswerPort saveAnswerPort;

  private QnaAcceptStateSyncAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new QnaAcceptStateSyncAdapter(postPersistencePort, loadAnswerPort, saveAnswerPort);
  }

  @Test
  @DisplayName("confirmAccepted loads answer before post and saves both")
  void confirmAccepted_loadsAnswerBeforePost() {
    Answer answer = answer(201L, 101L, false);
    Post post = pendingPost(101L, 201L);
    when(loadAnswerPort.loadAnswerForUpdate(201L)).thenReturn(Optional.of(answer));
    when(postPersistencePort.loadPostForUpdate(101L)).thenReturn(Optional.of(post));

    adapter.confirmAccepted(101L, 201L);

    InOrder inOrder = inOrder(loadAnswerPort, postPersistencePort, saveAnswerPort);
    inOrder.verify(loadAnswerPort).loadAnswerForUpdate(201L);
    inOrder.verify(postPersistencePort).loadPostForUpdate(101L);
    inOrder
        .verify(postPersistencePort)
        .savePost(
            org.mockito.ArgumentMatchers.argThat(
                savedPost ->
                    savedPost.getId().equals(101L)
                        && savedPost.getStatus() == PostStatus.RESOLVED
                        && savedPost.getAcceptedAnswerId().equals(201L)));
    inOrder
        .verify(saveAnswerPort)
        .saveAnswer(
            org.mockito.ArgumentMatchers.argThat(
                savedAnswer ->
                    savedAnswer.getId().equals(201L)
                        && savedAnswer.getPostId().equals(101L)
                        && savedAnswer.getIsAccepted()));
  }

  @Test
  @DisplayName("confirmAccepted throws when answer belongs to different post")
  void confirmAccepted_throwsWhenAnswerPostMismatch() {
    when(loadAnswerPort.loadAnswerForUpdate(201L))
        .thenReturn(Optional.of(answer(201L, 999L, false)));
    when(postPersistencePort.loadPostForUpdate(101L))
        .thenReturn(Optional.of(pendingPost(101L, 201L)));

    assertThatThrownBy(() -> adapter.confirmAccepted(101L, 201L))
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("does not belong");

    verifyNoInteractions(saveAnswerPort);
  }

  @Test
  @DisplayName("confirmAccepted throws when answer is missing")
  void confirmAccepted_throwsWhenAnswerMissing() {
    when(loadAnswerPort.loadAnswerForUpdate(201L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> adapter.confirmAccepted(101L, 201L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("missing answer");
  }

  @Test
  @DisplayName("confirmAccepted throws when post is missing")
  void confirmAccepted_throwsWhenPostMissing() {
    when(loadAnswerPort.loadAnswerForUpdate(201L))
        .thenReturn(Optional.of(answer(201L, 101L, false)));
    when(postPersistencePort.loadPostForUpdate(101L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> adapter.confirmAccepted(101L, 201L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("missing post");
  }

  @Test
  @DisplayName("rollbackPendingAccept loads answer before post and reopens post")
  void rollbackPendingAccept_loadsAnswerBeforePost() {
    when(loadAnswerPort.loadAnswerForUpdate(201L))
        .thenReturn(Optional.of(answer(201L, 101L, false)));
    Post post = pendingPost(101L, 201L);
    when(postPersistencePort.loadPostForUpdate(101L)).thenReturn(Optional.of(post));

    adapter.rollbackPendingAccept(101L, 201L);

    InOrder inOrder = inOrder(loadAnswerPort, postPersistencePort);
    inOrder.verify(loadAnswerPort).loadAnswerForUpdate(201L);
    inOrder.verify(postPersistencePort).loadPostForUpdate(101L);
    inOrder
        .verify(postPersistencePort)
        .savePost(
            org.mockito.ArgumentMatchers.argThat(
                savedPost ->
                    savedPost.getId().equals(101L)
                        && savedPost.getStatus() == PostStatus.OPEN
                        && savedPost.getAcceptedAnswerId() == null));
    verifyNoInteractions(saveAnswerPort);
  }

  @Test
  @DisplayName("rollbackPendingAccept throws when answer is missing")
  void rollbackPendingAccept_throwsWhenAnswerMissing() {
    when(loadAnswerPort.loadAnswerForUpdate(201L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> adapter.rollbackPendingAccept(101L, 201L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("missing answer");
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

  private Answer answer(Long answerId, Long postId, boolean accepted) {
    return Answer.builder()
        .id(answerId)
        .postId(postId)
        .userId(2L)
        .content("answer")
        .isAccepted(accepted)
        .createdAt(LocalDateTime.of(2026, 1, 1, 9, 0))
        .updatedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
        .build();
  }
}
