package momzzangseven.mztkbe.modules.post.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.post.application.dto.ModeratePostCommand;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostModerationStatus;
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
@DisplayName("ModeratePostService unit test")
class ModeratePostServiceTest {

  @Mock private PostPersistencePort postPersistencePort;

  @InjectMocks private ModeratePostService service;

  @Test
  @DisplayName("blockPost sets moderation status to BLOCKED without changing publication status")
  void blockPostSetsBlocked() {
    Post post = post(PostPublicationStatus.FAILED, PostModerationStatus.NORMAL);
    when(postPersistencePort.loadPostForUpdate(10L)).thenReturn(Optional.of(post));
    when(postPersistencePort.savePost(org.mockito.ArgumentMatchers.any(Post.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.blockPost(new ModeratePostCommand(99L, 10L));

    ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
    verify(postPersistencePort).savePost(postCaptor.capture());
    assertThat(postCaptor.getValue().getModerationStatus()).isEqualTo(PostModerationStatus.BLOCKED);
    assertThat(postCaptor.getValue().getPublicationStatus())
        .isEqualTo(PostPublicationStatus.FAILED);
  }

  @Test
  @DisplayName(
      "unblockPost restores moderation status to NORMAL without changing publication status")
  void unblockPostSetsNormal() {
    Post post = post(PostPublicationStatus.PENDING, PostModerationStatus.BLOCKED);
    when(postPersistencePort.loadPostForUpdate(10L)).thenReturn(Optional.of(post));
    when(postPersistencePort.savePost(org.mockito.ArgumentMatchers.any(Post.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.unblockPost(new ModeratePostCommand(99L, 10L));

    ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
    verify(postPersistencePort).savePost(postCaptor.capture());
    assertThat(postCaptor.getValue().getModerationStatus()).isEqualTo(PostModerationStatus.NORMAL);
    assertThat(postCaptor.getValue().getPublicationStatus())
        .isEqualTo(PostPublicationStatus.PENDING);
  }

  private Post post(
      PostPublicationStatus publicationStatus, PostModerationStatus moderationStatus) {
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
        .moderationStatus(moderationStatus)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }
}
