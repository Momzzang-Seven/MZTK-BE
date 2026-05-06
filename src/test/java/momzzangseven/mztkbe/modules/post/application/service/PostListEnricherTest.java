package momzzangseven.mztkbe.modules.post.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import momzzangseven.mztkbe.modules.post.application.dto.PostImageResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostImageResult.PostImageSlot;
import momzzangseven.mztkbe.modules.post.application.dto.PostListResult;
import momzzangseven.mztkbe.modules.post.application.port.out.CountAnswersPort;
import momzzangseven.mztkbe.modules.post.application.port.out.CountCommentsPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostImagesPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostWriterPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostLikePersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostLikeTargetType;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostListEnricher unit test")
class PostListEnricherTest {

  @Mock private CountCommentsPort countCommentsPort;
  @Mock private CountAnswersPort countAnswersPort;
  @Mock private LoadTagPort loadTagPort;
  @Mock private LoadPostWriterPort loadPostWriterPort;
  @Mock private PostLikePersistencePort postLikePersistencePort;
  @Mock private LoadPostImagesPort loadPostImagesPort;

  @InjectMocks private PostListEnricher postListEnricher;

  @Test
  @DisplayName("enrich maps tags, writer, counts, liked status, images, and question metadata")
  void enrich_mapsPostListFields() {
    Post free = post(1L, 10L, PostType.FREE, PostStatus.OPEN, null);
    Post question = post(2L, 20L, PostType.QUESTION, PostStatus.PENDING_ACCEPT, 99L);

    when(loadTagPort.findTagsByPostIdsIn(List.of(1L, 2L)))
        .thenReturn(Map.of(1L, List.of("routine"), 2L, List.of("squat", "form")));
    when(loadPostWriterPort.loadWritersByIds(Set.of(10L, 20L)))
        .thenReturn(
            Map.of(
                10L, new LoadPostWriterPort.WriterSummary(10L, "free-writer", "free.png"),
                20L, new LoadPostWriterPort.WriterSummary(20L, "q-writer", null)));
    when(postLikePersistencePort.countByTargetIds(PostLikeTargetType.POST, List.of(1L, 2L)))
        .thenReturn(Map.of(1L, 5L, 2L, 7L));
    when(countCommentsPort.countCommentsByPostIds(List.of(1L, 2L)))
        .thenReturn(Map.of(1L, 3L, 2L, 4L));
    when(countAnswersPort.countAnswersByPostIds(List.of(2L))).thenReturn(Map.of(2L, 6L));
    when(postLikePersistencePort.findLikedTargetIds(PostLikeTargetType.POST, List.of(1L, 2L), 99L))
        .thenReturn(Set.of(2L));
    when(loadPostImagesPort.loadImagesByPostIds(
            Map.of(PostType.FREE, List.of(1L), PostType.QUESTION, List.of(2L))))
        .thenReturn(
            Map.of(
                1L, new PostImageResult(List.of(new PostImageSlot(101L, "free.webp"))),
                2L, new PostImageResult(List.of(new PostImageSlot(201L, "question.webp")))));

    List<PostListResult> results = postListEnricher.enrich(List.of(free, question), 99L);

    assertThat(results).hasSize(2);
    assertThat(results.get(0).postId()).isEqualTo(1L);
    assertThat(results.get(0).tags()).containsExactly("routine");
    assertThat(results.get(0).nickname()).isEqualTo("free-writer");
    assertThat(results.get(0).profileImageUrl()).isEqualTo("free.png");
    assertThat(results.get(0).likeCount()).isEqualTo(5L);
    assertThat(results.get(0).commentCount()).isEqualTo(3L);
    assertThat(results.get(0).answerCount()).isZero();
    assertThat(results.get(0).liked()).isFalse();
    assertThat(results.get(0).images()).containsExactly(new PostImageSlot(101L, "free.webp"));

    assertThat(results.get(1).postId()).isEqualTo(2L);
    assertThat(results.get(1).tags()).containsExactly("squat", "form");
    assertThat(results.get(1).nickname()).isEqualTo("q-writer");
    assertThat(results.get(1).profileImageUrl()).isNull();
    assertThat(results.get(1).likeCount()).isEqualTo(7L);
    assertThat(results.get(1).commentCount()).isEqualTo(4L);
    assertThat(results.get(1).answerCount()).isEqualTo(6L);
    assertThat(results.get(1).liked()).isTrue();
    assertThat(results.get(1).reward()).isEqualTo(100L);
    assertThat(results.get(1).isSolved()).isTrue();
    assertThat(results.get(1).images()).containsExactly(new PostImageSlot(201L, "question.webp"));
  }

  @Test
  @DisplayName("enrich uses zero counts and empty tags/images when optional maps have no entry")
  void enrich_usesDefaultValues() {
    Post post = post(1L, 10L, PostType.FREE, PostStatus.OPEN, null);

    when(loadTagPort.findTagsByPostIdsIn(List.of(1L))).thenReturn(Map.of());
    when(loadPostWriterPort.loadWritersByIds(Set.of(10L))).thenReturn(Map.of());
    when(postLikePersistencePort.countByTargetIds(PostLikeTargetType.POST, List.of(1L)))
        .thenReturn(Map.of());
    when(countCommentsPort.countCommentsByPostIds(List.of(1L))).thenReturn(null);
    when(countAnswersPort.countAnswersByPostIds(List.of())).thenReturn(Map.of());
    when(postLikePersistencePort.findLikedTargetIds(PostLikeTargetType.POST, List.of(1L), 99L))
        .thenReturn(Set.of());
    when(loadPostImagesPort.loadImagesByPostIds(Map.of(PostType.FREE, List.of(1L))))
        .thenReturn(Map.of());

    List<PostListResult> results = postListEnricher.enrich(List.of(post), 99L);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().tags()).isEmpty();
    assertThat(results.getFirst().nickname()).isNull();
    assertThat(results.getFirst().profileImageUrl()).isNull();
    assertThat(results.getFirst().likeCount()).isZero();
    assertThat(results.getFirst().commentCount()).isZero();
    assertThat(results.getFirst().answerCount()).isZero();
    assertThat(results.getFirst().liked()).isFalse();
    assertThat(results.getFirst().images()).isEmpty();
  }

  @Test
  @DisplayName("enrichAllLiked marks all posts liked without loading requester liked ids")
  void enrichAllLiked_marksAllLiked() {
    Post first = post(1L, 10L, PostType.FREE, PostStatus.OPEN, null);
    Post second = post(2L, 20L, PostType.FREE, PostStatus.OPEN, null);

    when(loadTagPort.findTagsByPostIdsIn(List.of(1L, 2L))).thenReturn(Map.of());
    when(loadPostWriterPort.loadWritersByIds(Set.of(10L, 20L))).thenReturn(Map.of());
    when(postLikePersistencePort.countByTargetIds(PostLikeTargetType.POST, List.of(1L, 2L)))
        .thenReturn(Map.of());
    when(countCommentsPort.countCommentsByPostIds(List.of(1L, 2L))).thenReturn(Map.of());
    when(countAnswersPort.countAnswersByPostIds(List.of())).thenReturn(Map.of());
    when(loadPostImagesPort.loadImagesByPostIds(Map.of(PostType.FREE, List.of(1L, 2L))))
        .thenReturn(Map.of());

    List<PostListResult> results = postListEnricher.enrichAllLiked(List.of(first, second));

    assertThat(results).extracting(PostListResult::postId).containsExactly(1L, 2L);
    assertThat(results).allSatisfy(result -> assertThat(result.liked()).isTrue());
    verify(postLikePersistencePort, never()).findLikedTargetIds(any(), any(), any());
  }

  private Post post(Long id, Long userId, PostType type, PostStatus status, Long acceptedAnswerId) {
    return Post.builder()
        .id(id)
        .userId(userId)
        .type(type)
        .title(type == PostType.QUESTION ? "question title" : null)
        .content("content-" + id)
        .reward(type == PostType.QUESTION ? 100L : 0L)
        .acceptedAnswerId(acceptedAnswerId)
        .status(status)
        .createdAt(LocalDateTime.of(2026, 4, 26, 12, 0).minusHours(id))
        .updatedAt(LocalDateTime.of(2026, 4, 26, 12, 0).minusHours(id))
        .build();
  }
}
