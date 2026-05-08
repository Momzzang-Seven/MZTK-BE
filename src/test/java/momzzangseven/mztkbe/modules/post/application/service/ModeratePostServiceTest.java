package momzzangseven.mztkbe.modules.post.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.post.PostNotFoundException;
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

    var result = service.blockPost(new ModeratePostCommand(99L, 10L));

    ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
    verify(postPersistencePort).savePost(postCaptor.capture());
    assertThat(postCaptor.getValue().getModerationStatus()).isEqualTo(PostModerationStatus.BLOCKED);
    assertThat(postCaptor.getValue().getPublicationStatus())
        .isEqualTo(PostPublicationStatus.FAILED);
    assertThat(result.moderated()).isTrue();
    assertThat(result.publicationStatus()).isEqualTo(PostPublicationStatus.FAILED);
    assertThat(result.moderationStatus()).isEqualTo(PostModerationStatus.BLOCKED);
  }

  @Test
  @DisplayName("blockPost returns moderated=false when post is already BLOCKED")
  void blockPostAlreadyBlockedReturnsNotModerated() {
    Post post = post(PostPublicationStatus.VISIBLE, PostModerationStatus.BLOCKED);
    when(postPersistencePort.loadPostForUpdate(10L)).thenReturn(Optional.of(post));

    var result = service.blockPost(new ModeratePostCommand(99L, 10L));

    verify(postPersistencePort, never()).savePost(org.mockito.ArgumentMatchers.any(Post.class));
    assertThat(result.moderated()).isFalse();
    assertThat(result.publicationStatus()).isEqualTo(PostPublicationStatus.VISIBLE);
    assertThat(result.moderationStatus()).isEqualTo(PostModerationStatus.BLOCKED);
  }

  @Test
  @DisplayName("blockManagedPost reuses block logic without changing publication status")
  void blockManagedPostSetsBlocked() {
    Post post = post(PostPublicationStatus.VISIBLE, PostModerationStatus.NORMAL);
    when(postPersistencePort.loadPostForUpdate(10L)).thenReturn(Optional.of(post));
    when(postPersistencePort.savePost(org.mockito.ArgumentMatchers.any(Post.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var result = service.blockManagedPost(new ModeratePostCommand(99L, 10L));

    ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
    verify(postPersistencePort).savePost(postCaptor.capture());
    assertThat(postCaptor.getValue().getPublicationStatus())
        .isEqualTo(PostPublicationStatus.VISIBLE);
    assertThat(postCaptor.getValue().getModerationStatus()).isEqualTo(PostModerationStatus.BLOCKED);
    assertThat(result.moderated()).isTrue();
    assertThat(result.publicationStatus()).isEqualTo(PostPublicationStatus.VISIBLE);
    assertThat(result.moderationStatus()).isEqualTo(PostModerationStatus.BLOCKED);
  }

  @Test
  @DisplayName(
      "unblockPost restores moderation status to NORMAL without changing publication status")
  void unblockPostSetsNormal() {
    Post post = post(PostPublicationStatus.PENDING, PostModerationStatus.BLOCKED);
    when(postPersistencePort.loadPostForUpdate(10L)).thenReturn(Optional.of(post));
    when(postPersistencePort.savePost(org.mockito.ArgumentMatchers.any(Post.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var result = service.unblockPost(new ModeratePostCommand(99L, 10L));

    ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
    verify(postPersistencePort).savePost(postCaptor.capture());
    assertThat(postCaptor.getValue().getModerationStatus()).isEqualTo(PostModerationStatus.NORMAL);
    assertThat(postCaptor.getValue().getPublicationStatus())
        .isEqualTo(PostPublicationStatus.PENDING);
    assertThat(result.moderated()).isTrue();
    assertThat(result.publicationStatus()).isEqualTo(PostPublicationStatus.PENDING);
    assertThat(result.moderationStatus()).isEqualTo(PostModerationStatus.NORMAL);
  }

  @Test
  @DisplayName(
      "unblockPost keeps FAILED publication status while restoring NORMAL moderation status")
  void unblockPostFailedBlockedKeepsPublicationStatus() {
    Post post = post(PostPublicationStatus.FAILED, PostModerationStatus.BLOCKED);
    when(postPersistencePort.loadPostForUpdate(10L)).thenReturn(Optional.of(post));
    when(postPersistencePort.savePost(org.mockito.ArgumentMatchers.any(Post.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var result = service.unblockPost(new ModeratePostCommand(99L, 10L));

    ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
    verify(postPersistencePort).savePost(postCaptor.capture());
    assertThat(postCaptor.getValue().getModerationStatus()).isEqualTo(PostModerationStatus.NORMAL);
    assertThat(postCaptor.getValue().getPublicationStatus())
        .isEqualTo(PostPublicationStatus.FAILED);
    assertThat(result.publicationStatus()).isEqualTo(PostPublicationStatus.FAILED);
    assertThat(result.moderationStatus()).isEqualTo(PostModerationStatus.NORMAL);
  }

  @Test
  @DisplayName("unblockPost returns moderated=false when post is already NORMAL")
  void unblockPostAlreadyNormalReturnsNotModerated() {
    Post post = post(PostPublicationStatus.FAILED, PostModerationStatus.NORMAL);
    when(postPersistencePort.loadPostForUpdate(10L)).thenReturn(Optional.of(post));

    var result = service.unblockPost(new ModeratePostCommand(99L, 10L));

    verify(postPersistencePort, never()).savePost(org.mockito.ArgumentMatchers.any(Post.class));
    assertThat(result.moderated()).isFalse();
    assertThat(result.publicationStatus()).isEqualTo(PostPublicationStatus.FAILED);
    assertThat(result.moderationStatus()).isEqualTo(PostModerationStatus.NORMAL);
  }

  @Test
  @DisplayName("unblockManagedPost keeps FAILED publication status while unblocking")
  void unblockManagedPostFailedBlockedKeepsPublicationStatus() {
    Post post = post(PostPublicationStatus.FAILED, PostModerationStatus.BLOCKED);
    when(postPersistencePort.loadPostForUpdate(10L)).thenReturn(Optional.of(post));
    when(postPersistencePort.savePost(org.mockito.ArgumentMatchers.any(Post.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var result = service.unblockManagedPost(new ModeratePostCommand(99L, 10L));

    ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
    verify(postPersistencePort).savePost(postCaptor.capture());
    assertThat(postCaptor.getValue().getPublicationStatus())
        .isEqualTo(PostPublicationStatus.FAILED);
    assertThat(postCaptor.getValue().getModerationStatus()).isEqualTo(PostModerationStatus.NORMAL);
    assertThat(result.moderated()).isTrue();
    assertThat(result.publicationStatus()).isEqualTo(PostPublicationStatus.FAILED);
    assertThat(result.moderationStatus()).isEqualTo(PostModerationStatus.NORMAL);
  }

  @Test
  @DisplayName("blockPost throws PostNotFoundException when post does not exist")
  void blockPostNotFoundThrows() {
    when(postPersistencePort.loadPostForUpdate(10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.blockPost(new ModeratePostCommand(99L, 10L)))
        .isInstanceOf(PostNotFoundException.class);
  }

  @Test
  @DisplayName("unblockPost throws PostNotFoundException when post does not exist")
  void unblockPostNotFoundThrows() {
    when(postPersistencePort.loadPostForUpdate(10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.unblockPost(new ModeratePostCommand(99L, 10L)))
        .isInstanceOf(PostNotFoundException.class);
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
