package momzzangseven.mztkbe.modules.post.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import momzzangseven.mztkbe.modules.post.application.dto.PostSearchCondition;
import momzzangseven.mztkbe.modules.post.application.dto.SearchPostsResult;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostWriterPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostLikePersistencePort;
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

    SearchPostsResult results = searchPostsService.searchPosts(condition, 99L);

    assertThat(results.posts()).isEmpty();
    assertThat(results.hasNext()).isFalse();
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

    SearchPostsResult results = searchPostsService.searchPosts(condition, 99L);

    assertThat(results.posts()).hasSize(1);
    assertThat(results.posts().getFirst().tags()).isEmpty();
    assertThat(results.hasNext()).isFalse();
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

    SearchPostsResult results = searchPostsService.searchPosts(condition, 99L);

    assertThat(results.posts()).hasSize(2);
    assertThat(results.posts().get(0).tags()).containsExactly("java");
    assertThat(results.posts().get(1).tags()).containsExactly("spring", "kotlin");
    assertThat(results.posts().get(0).likeCount()).isEqualTo(2L);
    assertThat(results.posts().get(1).liked()).isTrue();
    assertThat(results.hasNext()).isFalse();
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

    SearchPostsResult results = searchPostsService.searchPosts(condition, 99L);

    assertThat(results.posts()).hasSize(1);
    assertThat(results.posts().getFirst().nickname()).isEqualTo("writer");
    assertThat(results.posts().getFirst().profileImageUrl()).isEqualTo("profile.png");
    assertThat(results.posts().getFirst().tags()).containsExactly("java");
    assertThat(results.hasNext()).isFalse();
  }

  @Test
  @DisplayName("maps pending accept question as solved in search results")
  void searchPostsMapsPendingAcceptAsSolved() {
    PostSearchCondition condition = PostSearchCondition.of(PostType.QUESTION, null, null, 0, 10);
    Post post =
        Post.builder()
            .id(3L)
            .userId(1L)
            .type(PostType.QUESTION)
            .title("title")
            .content("content")
            .reward(50L)
            .acceptedAnswerId(77L)
            .status(PostStatus.PENDING_ACCEPT)
            .build();

    when(postPersistencePort.findPostsByCondition(condition, null)).thenReturn(List.of(post));
    when(loadTagPort.findTagsByPostIdsIn(List.of(3L))).thenReturn(Map.of());
    when(loadPostWriterPort.loadWritersByIds(Set.of(1L))).thenReturn(Map.of());
    when(postLikePersistencePort.countByTargetIds(any(), any())).thenReturn(Map.of());
    when(postLikePersistencePort.findLikedTargetIds(any(), any(), any())).thenReturn(Set.of());

    SearchPostsResult results = searchPostsService.searchPosts(condition, 99L);

    assertThat(results.posts()).hasSize(1);
    assertThat(results.posts().getFirst().isSolved()).isTrue();
    assertThat(results.hasNext()).isFalse();
  }

  @Test
  @DisplayName("returns empty when persistence returns no posts")
  void searchPostsReturnsEmptyWhenNoPostFound() {
    PostSearchCondition condition = PostSearchCondition.of(PostType.QUESTION, null, "query", 0, 10);

    when(postPersistencePort.findPostsByCondition(condition, null)).thenReturn(List.of());

    SearchPostsResult results = searchPostsService.searchPosts(condition, 99L);

    assertThat(results.posts()).isEmpty();
    assertThat(results.hasNext()).isFalse();
    verify(loadTagPort, never()).findTagsByPostIdsIn(List.of());
  }

  @Test
  @DisplayName("calculates hasNext=true and trims probe row to requested size")
  void searchPostsCalculatesHasNextAndTrimsToRequestedSize() {
    PostSearchCondition condition = PostSearchCondition.of(PostType.FREE, null, null, 0, 2);
    Post first = post(1L);
    Post second = post(2L);
    Post probe = post(3L);

    when(postPersistencePort.findPostsByCondition(condition, null))
        .thenReturn(List.of(first, second, probe));
    when(loadTagPort.findTagsByPostIdsIn(List.of(1L, 2L)))
        .thenReturn(Map.of(1L, List.of("java"), 2L, List.of("spring")));
    when(loadPostWriterPort.loadWritersByIds(Set.of(1L))).thenReturn(Map.of());
    when(postLikePersistencePort.countByTargetIds(any(), any())).thenReturn(Map.of(1L, 2L, 2L, 1L));
    when(postLikePersistencePort.findLikedTargetIds(any(), any(), any())).thenReturn(Set.of(2L));

    SearchPostsResult results = searchPostsService.searchPosts(condition, 99L);

    assertThat(results.hasNext()).isTrue();
    assertThat(results.posts()).hasSize(2);
    assertThat(results.posts().get(0).postId()).isEqualTo(1L);
    assertThat(results.posts().get(1).postId()).isEqualTo(2L);
    verify(loadTagPort).findTagsByPostIdsIn(List.of(1L, 2L));
  }

  private Post post(Long id) {
    return Post.builder()
        .id(id)
        .userId(1L)
        .type(PostType.FREE)
        .title("title")
        .content("content")
        .reward(0L)
        .status(PostStatus.OPEN)
        .build();
  }
}
