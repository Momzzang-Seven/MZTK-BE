package momzzangseven.mztkbe.modules.post.application.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.event.PostDeletedEvent;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConfirmQuestionDeleteSyncService unit test")
class ConfirmQuestionDeleteSyncServiceTest {

  @Mock private PostPersistencePort postPersistencePort;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private ConfirmQuestionDeleteSyncService confirmQuestionDeleteSyncService;

  @Test
  @DisplayName("confirmed question delete removes the local post and publishes the domain event")
  void confirmDeleted_removesPostAndPublishesEvent() {
    Post post = questionPost(101L);
    when(postPersistencePort.loadPostForUpdate(101L)).thenReturn(Optional.of(post));

    confirmQuestionDeleteSyncService.confirmDeleted(101L);

    verify(postPersistencePort).deletePost(post);
    verify(eventPublisher).publishEvent(new PostDeletedEvent(101L, PostType.QUESTION));
  }

  @Test
  @DisplayName("missing local question row is ignored for idempotent confirmation")
  void confirmDeleted_ignoresMissingPost() {
    when(postPersistencePort.loadPostForUpdate(101L)).thenReturn(Optional.empty());

    confirmQuestionDeleteSyncService.confirmDeleted(101L);

    verify(postPersistencePort, never()).deletePost(org.mockito.ArgumentMatchers.any());
    verifyNoInteractions(eventPublisher);
  }

  private Post questionPost(Long postId) {
    return Post.builder()
        .id(postId)
        .userId(7L)
        .type(PostType.QUESTION)
        .title("질문 제목")
        .content("질문 내용")
        .reward(50L)
        .status(PostStatus.OPEN)
        .createdAt(LocalDateTime.of(2026, 1, 1, 9, 0))
        .updatedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
        .build();
  }
}
