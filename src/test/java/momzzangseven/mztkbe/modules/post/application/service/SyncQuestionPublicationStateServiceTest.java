package momzzangseven.mztkbe.modules.post.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.post.application.dto.SyncQuestionPublicationStateCommand;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
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
@DisplayName("SyncQuestionPublicationStateService unit test")
class SyncQuestionPublicationStateServiceTest {

  @Mock private PostPersistencePort postPersistencePort;

  @InjectMocks private SyncQuestionPublicationStateService service;

  @Test
  @DisplayName("confirmed create marks pending question visible")
  void confirmQuestionCreatedMarksVisible() {
    Post post = questionPost(PostPublicationStatus.PENDING);
    when(postPersistencePort.loadPostForUpdate(10L)).thenReturn(Optional.of(post));

    service.confirmQuestionCreated(
        new SyncQuestionPublicationStateCommand(10L, "intent-1", null, null));

    ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
    verify(postPersistencePort).savePost(postCaptor.capture());
    assertThat(postCaptor.getValue().getPublicationStatus())
        .isEqualTo(PostPublicationStatus.VISIBLE);
  }

  @Test
  @DisplayName("terminal create failure marks pending question failed")
  void failQuestionCreateMarksFailed() {
    Post post = questionPost(PostPublicationStatus.PENDING);
    when(postPersistencePort.loadPostForUpdate(10L)).thenReturn(Optional.of(post));

    service.failQuestionCreate(
        new SyncQuestionPublicationStateCommand(
            10L, "intent-1", "FAILED_ONCHAIN", "execution failed"));

    ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
    verify(postPersistencePort).savePost(postCaptor.capture());
    assertThat(postCaptor.getValue().getPublicationStatus())
        .isEqualTo(PostPublicationStatus.FAILED);
  }

  @Test
  @DisplayName("terminal create failure does not downgrade already visible question")
  void failQuestionCreateDoesNotDowngradeVisibleQuestion() {
    Post post = questionPost(PostPublicationStatus.VISIBLE);
    when(postPersistencePort.loadPostForUpdate(10L)).thenReturn(Optional.of(post));

    service.failQuestionCreate(
        new SyncQuestionPublicationStateCommand(10L, "intent-1", "EXPIRED", null));

    verify(postPersistencePort, never()).savePost(org.mockito.ArgumentMatchers.any(Post.class));
  }

  private Post questionPost(PostPublicationStatus publicationStatus) {
    LocalDateTime now = LocalDateTime.of(2026, 1, 1, 10, 0);
    return Post.builder()
        .id(10L)
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
