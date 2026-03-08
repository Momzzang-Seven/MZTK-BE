package momzzangseven.mztkbe.modules.post.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.post.PostNotFoundException;
import momzzangseven.mztkbe.modules.post.application.dto.PostResult;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetPostService unit test")
class GetPostServiceTest {

  @Mock private PostPersistencePort postPersistencePort;
  @Mock private LoadTagPort loadTagPort;

  @InjectMocks private GetPostService getPostService;

  @Test
  @DisplayName("returns mapped post with tags from tag module")
  void getPostSuccess() {
    LocalDateTime now = LocalDateTime.of(2026, 1, 1, 10, 0);
    Post post =
        Post.builder()
            .id(20L)
            .userId(8L)
            .type(PostType.FREE)
            .title("hello")
            .content("world")
            .reward(0L)
            .isSolved(null)
            .createdAt(now)
            .updatedAt(now)
            .build();

    when(postPersistencePort.loadPost(20L)).thenReturn(Optional.of(post));
    when(loadTagPort.findTagNamesByPostId(20L)).thenReturn(List.of("java", "spring"));

    PostResult result = getPostService.getPost(20L);

    assertThat(result.postId()).isEqualTo(20L);
    assertThat(result.tags()).containsExactly("java", "spring");
    assertThat(result.isSolved()).isFalse();
  }

  @Test
  @DisplayName("throws when post does not exist")
  void getPostThrowsWhenNotFound() {
    when(postPersistencePort.loadPost(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> getPostService.getPost(999L))
        .isInstanceOf(PostNotFoundException.class);

    verify(loadTagPort, never()).findTagNamesByPostId(999L);
  }
}
