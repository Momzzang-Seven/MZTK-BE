package momzzangseven.mztkbe.modules.post.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import momzzangseven.mztkbe.modules.post.application.port.in.GetPostContextUseCase.PostContext;
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

/**
 * MOM-459 chain proof — the read-path used by CommentService / AnswerService / etc. to resolve a
 * post's visibility context must never escalate to {@code loadPostForUpdate}. This service is the
 * branch point above {@link PostPersistencePort}, so verifying it here prevents a regression where
 * a future refactor reroutes the read path through the locking method (which would re-introduce the
 * row-lock contention that MOM-459 removed).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PostContextService unit test — MOM-459 read path lock-free guard")
class PostContextServiceTest {

  @Mock private PostPersistencePort postPersistencePort;
  @InjectMocks private PostContextService postContextService;

  @Test
  @DisplayName("getPostContext calls loadPost (lock-free) — never loadPostForUpdate")
  void getPostContext_usesLockFreeLoad() {
    Long postId = 42L;
    given(postPersistencePort.loadPost(postId)).willReturn(Optional.of(samplePost(postId)));

    Optional<PostContext> result = postContextService.getPostContext(postId);

    assertThat(result).isPresent();
    verify(postPersistencePort).loadPost(postId);
    verify(postPersistencePort, never()).loadPostForUpdate(anyLong());
  }

  @Test
  @DisplayName("getPostContextForUpdate calls loadPostForUpdate — never lock-free loadPost")
  void getPostContextForUpdate_usesLockingLoad() {
    Long postId = 43L;
    given(postPersistencePort.loadPostForUpdate(postId))
        .willReturn(Optional.of(samplePost(postId)));

    Optional<PostContext> result = postContextService.getPostContextForUpdate(postId);

    assertThat(result).isPresent();
    verify(postPersistencePort).loadPostForUpdate(postId);
    verify(postPersistencePort, never()).loadPost(anyLong());
  }

  private Post samplePost(Long id) {
    return Post.builder()
        .id(id)
        .userId(7L)
        .type(PostType.FREE)
        .content("c")
        .reward(0L)
        .status(PostStatus.OPEN)
        .build();
  }
}
