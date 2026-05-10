package momzzangseven.mztkbe.modules.post.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.post.AnswerNotBelongToPostException;
import momzzangseven.mztkbe.global.error.post.OnlyPostWriterCanAcceptException;
import momzzangseven.mztkbe.global.error.post.PostAlreadySolvedException;
import momzzangseven.mztkbe.modules.post.application.dto.AcceptAnswerCommand;
import momzzangseven.mztkbe.modules.post.application.dto.AcceptAnswerResult;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadAcceptedAnswerPort;
import momzzangseven.mztkbe.modules.post.application.port.out.MarkAcceptedAnswerPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AcceptAnswerService unit test")
class AcceptAnswerServiceTest {

  @Mock private PostPersistencePort postPersistencePort;
  @Mock private LoadAcceptedAnswerPort loadAcceptedAnswerPort;
  @Mock private MarkAcceptedAnswerPort markAcceptedAnswerPort;
  @Mock private QuestionLifecycleExecutionPort questionLifecycleExecutionPort;
  @Spy private PostVisibilityPolicy postVisibilityPolicy = new PostVisibilityPolicy();

  @InjectMocks private AcceptAnswerService acceptAnswerService;

  @Test
  @DisplayName("accepts an answer for a question post by its writer")
  void execute_acceptsAnswer() {
    AcceptAnswerCommand command = new AcceptAnswerCommand(10L, 20L, 1L);
    Post post = questionPost(10L, 1L, PostStatus.OPEN, null);
    Post acceptedPost = questionPost(10L, 1L, PostStatus.RESOLVED, 20L);

    when(loadAcceptedAnswerPort.loadAcceptedAnswerForUpdate(20L))
        .thenReturn(
            Optional.of(
                new LoadAcceptedAnswerPort.AcceptedAnswerInfo(20L, 10L, 2L, "answer content")));
    when(postPersistencePort.loadPostForUpdate(10L)).thenReturn(Optional.of(post));
    when(postPersistencePort.savePost(any(Post.class))).thenReturn(acceptedPost);
    when(questionLifecycleExecutionPort.prepareAnswerAccept(
            10L, 20L, 1L, 2L, "content", "answer content", 100L))
        .thenReturn(Optional.empty());

    AcceptAnswerResult result = acceptAnswerService.execute(command);

    assertThat(result.postId()).isEqualTo(10L);
    assertThat(result.acceptedAnswerId()).isEqualTo(20L);
    assertThat(result.status()).isEqualTo(PostStatus.RESOLVED);
    verify(postPersistencePort).savePost(any(Post.class));
    verify(markAcceptedAnswerPort).markAccepted(20L);
    verify(questionLifecycleExecutionPort)
        .prepareAnswerAccept(10L, 20L, 1L, 2L, "content", "answer content", 100L);
    var inOrder = inOrder(loadAcceptedAnswerPort, postPersistencePort);
    inOrder.verify(loadAcceptedAnswerPort).loadAcceptedAnswerForUpdate(20L);
    inOrder.verify(postPersistencePort).loadPostForUpdate(10L);
  }

  @Test
  @DisplayName("web3-managed accept keeps post pending until onchain confirmation")
  void execute_web3ManagedAccept_keepsPendingState() {
    AcceptAnswerCommand command = new AcceptAnswerCommand(10L, 20L, 1L);
    Post post = questionPost(10L, 1L, PostStatus.OPEN, null);
    Post pendingPost = questionPost(10L, 1L, PostStatus.PENDING_ACCEPT, 20L);

    given(questionLifecycleExecutionPort.managesAcceptLifecycle()).willReturn(true);
    when(loadAcceptedAnswerPort.loadAcceptedAnswerForUpdate(20L))
        .thenReturn(
            Optional.of(
                new LoadAcceptedAnswerPort.AcceptedAnswerInfo(20L, 10L, 2L, "answer content")));
    when(postPersistencePort.loadPostForUpdate(10L)).thenReturn(Optional.of(post));
    when(postPersistencePort.savePost(any(Post.class))).thenReturn(pendingPost);
    when(questionLifecycleExecutionPort.prepareAnswerAccept(
            10L, 20L, 1L, 2L, "content", "answer content", 100L))
        .thenReturn(Optional.empty());

    AcceptAnswerResult result = acceptAnswerService.execute(command);

    assertThat(result.status()).isEqualTo(PostStatus.PENDING_ACCEPT);
    assertThat(result.acceptedAnswerId()).isEqualTo(20L);
    verify(postPersistencePort).savePost(any(Post.class));
    verifyNoInteractions(markAcceptedAnswerPort);
    verify(questionLifecycleExecutionPort)
        .prepareAnswerAccept(10L, 20L, 1L, 2L, "content", "answer content", 100L);
  }

  @Test
  @DisplayName("rejects acceptance by a non-writer")
  void execute_throwsWhenRequesterIsNotWriter() {
    when(loadAcceptedAnswerPort.loadAcceptedAnswerForUpdate(20L))
        .thenReturn(
            Optional.of(
                new LoadAcceptedAnswerPort.AcceptedAnswerInfo(20L, 10L, 2L, "answer content")));
    when(postPersistencePort.loadPostForUpdate(10L))
        .thenReturn(Optional.of(questionPost(10L, 1L, PostStatus.OPEN, null)));

    assertThatThrownBy(() -> acceptAnswerService.execute(new AcceptAnswerCommand(10L, 20L, 3L)))
        .isInstanceOf(OnlyPostWriterCanAcceptException.class);
    verifyNoInteractions(markAcceptedAnswerPort);
    verifyNoInteractions(questionLifecycleExecutionPort);
  }

  @Test
  @DisplayName("rejects answers that belong to another post")
  void execute_throwsWhenAnswerDoesNotBelongToPost() {
    when(loadAcceptedAnswerPort.loadAcceptedAnswerForUpdate(20L))
        .thenReturn(
            Optional.of(
                new LoadAcceptedAnswerPort.AcceptedAnswerInfo(20L, 99L, 2L, "answer content")));
    when(postPersistencePort.loadPostForUpdate(10L))
        .thenReturn(Optional.of(questionPost(10L, 1L, PostStatus.OPEN, null)));

    assertThatThrownBy(() -> acceptAnswerService.execute(new AcceptAnswerCommand(10L, 20L, 1L)))
        .isInstanceOf(AnswerNotBelongToPostException.class);
    verifyNoInteractions(markAcceptedAnswerPort);
    verifyNoInteractions(questionLifecycleExecutionPort);
  }

  @Test
  @DisplayName("rejects already solved posts")
  void execute_throwsWhenPostAlreadySolved() {
    when(loadAcceptedAnswerPort.loadAcceptedAnswerForUpdate(20L))
        .thenReturn(
            Optional.of(
                new LoadAcceptedAnswerPort.AcceptedAnswerInfo(20L, 10L, 2L, "answer content")));
    when(postPersistencePort.loadPostForUpdate(10L))
        .thenReturn(Optional.of(questionPost(10L, 1L, PostStatus.RESOLVED, 30L)));

    assertThatThrownBy(() -> acceptAnswerService.execute(new AcceptAnswerCommand(10L, 20L, 1L)))
        .isInstanceOf(PostAlreadySolvedException.class);
    verifyNoInteractions(markAcceptedAnswerPort);
    verify(questionLifecycleExecutionPort, never())
        .prepareAnswerAccept(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("rejects admin refund pending posts")
  void execute_throwsWhenPostIsPendingAdminRefund() {
    when(loadAcceptedAnswerPort.loadAcceptedAnswerForUpdate(20L))
        .thenReturn(
            Optional.of(
                new LoadAcceptedAnswerPort.AcceptedAnswerInfo(20L, 10L, 2L, "answer content")));
    when(postPersistencePort.loadPostForUpdate(10L))
        .thenReturn(Optional.of(questionPost(10L, 1L, PostStatus.PENDING_ADMIN_REFUND, null)));

    assertThatThrownBy(() -> acceptAnswerService.execute(new AcceptAnswerCommand(10L, 20L, 1L)))
        .isInstanceOf(PostAlreadySolvedException.class);
    verifyNoInteractions(markAcceptedAnswerPort);
    verify(questionLifecycleExecutionPort, never())
        .prepareAnswerAccept(any(), any(), any(), any(), any(), any(), any());
  }

  private Post questionPost(Long id, Long userId, PostStatus status, Long acceptedAnswerId) {
    LocalDateTime now = LocalDateTime.of(2026, 1, 1, 10, 0);
    return Post.builder()
        .id(id)
        .userId(userId)
        .type(PostType.QUESTION)
        .title("question")
        .content("content")
        .reward(100L)
        .acceptedAnswerId(acceptedAnswerId)
        .status(status)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }
}
