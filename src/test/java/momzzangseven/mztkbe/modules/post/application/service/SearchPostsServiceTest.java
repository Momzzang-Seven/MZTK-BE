package momzzangseven.mztkbe.modules.post.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import momzzangseven.mztkbe.modules.post.application.dto.PostListResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostSearchCondition;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostWriterPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostLikePersistencePort;
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
@DisplayName("SearchPostsService unit test")
class SearchPostsServiceTest {

  @Mock private PostPersistencePort postPersistencePort;
  @Mock private LoadTagPort loadTagPort;
  @Mock private LoadPostWriterPort loadPostWriterPort;
  @Mock private PostLikePersistencePort postLikePersistencePort;

  @InjectMocks private SearchPostsService searchPostsService;

  @Test
  @DisplayName("returns empty immediately when tag filter has no matching post IDs")
  void searchPostsReturnsEmptyWhenTagFilterHasNoResults() {
    PostSearchCondition condition = PostSearchCondition.of(PostType.FREE, "java", null, 0, 10);

    when(loadTagPort.findPostIdsByTagName("java")).thenReturn(List.of());

    List<PostListResult> results = searchPostsService.searchPosts(condition, 99L);

    assertThat(results).isEmpty();
    verify(postPersistencePort, never()).findPostsByCondition(condition, List.of());
  }

  @Test
  @DisplayName("skips tag ID lookup when tagName is blank")
  void searchPostsSkipsTagLookupWhenTagNameBlank() {
    PostSearchCondition condition = PostSearchCondition.of(PostType.FREE, "   ", "hello", 0, 10);
    Post post = post(1L);

    when(postPersistencePort.findPostsByCondition(condition, null)).thenReturn(List.of(post));
    when(loadTagPort.findTagsByPostIdsIn(List.of(1L))).thenReturn(Map.of());
    when(loadPostWriterPort.loadWritersByIds(Set.of(1L))).thenReturn(Map.of());
    when(postLikePersistencePort.countByTargetIds(any(), any())).thenReturn(Map.of());
    when(postLikePersistencePort.findLikedTargetIds(any(), any(), any())).thenReturn(Set.of());

    List<PostListResult> results = searchPostsService.searchPosts(condition, 99L);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().tags()).isEmpty();
    verify(loadTagPort, never()).findPostIdsByTagName("   ");
  }

  @Test
  @DisplayName("enriches posts with tag map")
  void searchPostsEnrichesTags() {
    PostSearchCondition condition = PostSearchCondition.of(null, "java", "post", 0, 10);
    Post first = post(1L);
    Post second = post(2L);

    when(loadTagPort.findPostIdsByTagName("java")).thenReturn(List.of(1L, 2L));
    when(postPersistencePort.findPostsByCondition(condition, List.of(1L, 2L)))
        .thenReturn(List.of(first, second));
    when(loadTagPort.findTagsByPostIdsIn(List.of(1L, 2L)))
        .thenReturn(Map.of(1L, List.of("java"), 2L, List.of("spring", "kotlin")));
    when(loadPostWriterPort.loadWritersByIds(Set.of(1L))).thenReturn(Map.of());
    when(postLikePersistencePort.countByTargetIds(any(), any())).thenReturn(Map.of(1L, 2L, 2L, 1L));
    when(postLikePersistencePort.findLikedTargetIds(any(), any(), any())).thenReturn(Set.of(2L));

    List<PostListResult> results = searchPostsService.searchPosts(condition, 99L);

    assertThat(results).hasSize(2);
    assertThat(results.get(0).tags()).containsExactly("java");
    assertThat(results.get(1).tags()).containsExactly("spring", "kotlin");
    assertThat(results.get(0).likeCount()).isEqualTo(2L);
    assertThat(results.get(1).liked()).isTrue();
  }

  @Test
  @DisplayName("enriches posts with writer summary when writer exists")
  void searchPostsEnrichesWriterSummary() {
    PostSearchCondition condition = PostSearchCondition.of(PostType.FREE, null, null, 0, 10);
    Post post = post(1L);

    when(postPersistencePort.findPostsByCondition(condition, null)).thenReturn(List.of(post));
    when(loadTagPort.findTagsByPostIdsIn(List.of(1L))).thenReturn(Map.of(1L, List.of("java")));
    when(loadPostWriterPort.loadWritersByIds(Set.of(1L)))
        .thenReturn(Map.of(1L, new LoadPostWriterPort.WriterSummary(1L, "writer", "profile.png")));
    when(postLikePersistencePort.countByTargetIds(any(), any())).thenReturn(Map.of());
    when(postLikePersistencePort.findLikedTargetIds(any(), any(), any())).thenReturn(Set.of());

    List<PostListResult> results = searchPostsService.searchPosts(condition, 99L);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().nickname()).isEqualTo("writer");
    assertThat(results.getFirst().profileImageUrl()).isEqualTo("profile.png");
    assertThat(results.getFirst().tags()).containsExactly("java");
  }

  @Test
  @DisplayName("returns empty when persistence returns no posts")
  void searchPostsReturnsEmptyWhenNoPostFound() {
    PostSearchCondition condition = PostSearchCondition.of(PostType.QUESTION, null, "query", 0, 10);

    when(postPersistencePort.findPostsByCondition(condition, null)).thenReturn(List.of());

    List<PostListResult> results = searchPostsService.searchPosts(condition, 99L);

    assertThat(results).isEmpty();
    verify(loadTagPort, never()).findTagsByPostIdsIn(List.of());
  }

  private Post post(Long id) {
    return Post.builder()
        .id(id)
        .userId(1L)
        .type(PostType.FREE)
        .title("title")
        .content("content")
        .reward(0L)
        .isSolved(false)
        .build();
  }
}
