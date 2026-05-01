package momzzangseven.mztkbe.modules.post.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.global.error.pagination.InvalidCursorException;
import momzzangseven.mztkbe.global.error.post.InvalidLikedPostsQueryException;
import momzzangseven.mztkbe.global.pagination.CursorCodec;
import momzzangseven.mztkbe.modules.post.application.dto.GetMyLikedPostsCursorCommand;
import momzzangseven.mztkbe.modules.post.application.dto.GetMyLikedPostsCursorResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostListResult;
import momzzangseven.mztkbe.modules.post.application.port.out.LikedPostRow;
import momzzangseven.mztkbe.modules.post.application.port.out.PostLikePersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
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
@DisplayName("GetMyLikedPostsCursorService unit test")
class GetMyLikedPostsCursorServiceTest {

  @Mock private PostLikePersistencePort postLikePersistencePort;
  @Mock private PostListEnricher postListEnricher;

  @InjectMocks private GetMyLikedPostsCursorService service;

  @Test
  @DisplayName("execute returns empty result without enrichment when no liked rows")
  void execute_empty() {
    GetMyLikedPostsCursorCommand command =
        new GetMyLikedPostsCursorCommand(7L, PostType.FREE, null, null, 10);
    when(postLikePersistencePort.findLikedPostsByCursor(
            7L, PostType.FREE, null, command.pageRequest()))
        .thenReturn(List.of());

    GetMyLikedPostsCursorResult result = service.execute(command);

    assertThat(result.posts()).isEmpty();
    assertThat(result.hasNext()).isFalse();
    assertThat(result.nextCursor()).isNull();
    verifyNoInteractions(postListEnricher);
  }

  @Test
  @DisplayName("execute validates command before persistence")
  void execute_invalidCommandDoesNotQueryPersistence() {
    GetMyLikedPostsCursorCommand command =
        new GetMyLikedPostsCursorCommand(7L, null, null, null, 10);

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(InvalidLikedPostsQueryException.class);

    verifyNoInteractions(postLikePersistencePort);
    verifyNoInteractions(postListEnricher);
  }

  @Test
  @DisplayName("execute rejects invalid cursor before persistence")
  void execute_invalidCursorDoesNotQueryPersistence() {
    GetMyLikedPostsCursorCommand command =
        new GetMyLikedPostsCursorCommand(7L, PostType.FREE, null, "%%%", 10);

    assertThatThrownBy(() -> service.execute(command)).isInstanceOf(InvalidCursorException.class);

    verifyNoInteractions(postLikePersistencePort);
    verifyNoInteractions(postListEnricher);
  }

  @Test
  @DisplayName(
      "execute trims probe row, preserves order through enrichment, and builds next cursor")
  void execute_trimsProbeAndBuildsNextCursor() {
    GetMyLikedPostsCursorCommand command =
        new GetMyLikedPostsCursorCommand(7L, PostType.FREE, null, null, 2);
    LocalDateTime firstLikedAt = LocalDateTime.of(2026, 4, 26, 12, 0);
    LocalDateTime secondLikedAt = LocalDateTime.of(2026, 4, 26, 11, 0);
    LocalDateTime probeLikedAt = LocalDateTime.of(2026, 4, 26, 10, 0);
    LikedPostRow first = new LikedPostRow(post(1L), 101L, firstLikedAt);
    LikedPostRow second = new LikedPostRow(post(2L), 100L, secondLikedAt);
    LikedPostRow probe = new LikedPostRow(post(3L), 99L, probeLikedAt);
    PostListResult firstResult = result(1L);
    PostListResult secondResult = result(2L);
    when(postLikePersistencePort.findLikedPostsByCursor(
            7L, PostType.FREE, null, command.pageRequest()))
        .thenReturn(List.of(first, second, probe));
    when(postListEnricher.enrichAllLiked(any())).thenReturn(List.of(firstResult, secondResult));

    GetMyLikedPostsCursorResult result = service.execute(command);

    assertThat(result.posts()).extracting(PostListResult::postId).containsExactly(1L, 2L);
    assertThat(result.hasNext()).isTrue();
    assertThat(result.nextCursor()).isNotNull();
    assertThat(CursorCodec.decode(result.nextCursor(), command.pageRequest().scope()).id())
        .isEqualTo(100L);
    assertThat(CursorCodec.decode(result.nextCursor(), command.pageRequest().scope()).createdAt())
        .isEqualTo(secondLikedAt);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Post>> captor = ArgumentCaptor.forClass(List.class);
    verify(postListEnricher).enrichAllLiked(captor.capture());
    assertThat(captor.getValue().stream().map(Post::getId)).containsExactly(1L, 2L);
  }

  @Test
  @DisplayName("execute forwards QUESTION type and marks rows through liked-list enrichment")
  void execute_questionType() {
    GetMyLikedPostsCursorCommand command =
        new GetMyLikedPostsCursorCommand(7L, PostType.QUESTION, " Form ", null, 10);
    LikedPostRow question =
        new LikedPostRow(post(10L, PostType.QUESTION), 200L, LocalDateTime.of(2026, 4, 26, 12, 0));
    PostListResult questionResult = result(10L, PostType.QUESTION);
    when(postLikePersistencePort.findLikedPostsByCursor(
            7L, PostType.QUESTION, "form", command.pageRequest()))
        .thenReturn(List.of(question));
    when(postListEnricher.enrichAllLiked(any())).thenReturn(List.of(questionResult));

    GetMyLikedPostsCursorResult result = service.execute(command);

    assertThat(result.posts()).hasSize(1);
    assertThat(result.posts().getFirst().type()).isEqualTo(PostType.QUESTION);
    assertThat(result.posts().getFirst().liked()).isTrue();
    verify(postLikePersistencePort)
        .findLikedPostsByCursor(7L, PostType.QUESTION, "form", command.pageRequest());
  }

  @Test
  @DisplayName("execute does not create next cursor for last page")
  void execute_lastPage() {
    GetMyLikedPostsCursorCommand command =
        new GetMyLikedPostsCursorCommand(7L, PostType.FREE, "ignored", null, 10);
    LikedPostRow row = new LikedPostRow(post(1L), 101L, LocalDateTime.of(2026, 4, 26, 12, 0));
    when(postLikePersistencePort.findLikedPostsByCursor(
            7L, PostType.FREE, null, command.pageRequest()))
        .thenReturn(List.of(row));
    when(postListEnricher.enrichAllLiked(any())).thenReturn(List.of(result(1L)));

    GetMyLikedPostsCursorResult result = service.execute(command);

    assertThat(result.hasNext()).isFalse();
    assertThat(result.nextCursor()).isNull();
    verify(postLikePersistencePort, never())
        .findLikedPostsByCursor(7L, PostType.QUESTION, null, null);
  }

  private Post post(Long id) {
    return post(id, PostType.FREE);
  }

  private Post post(Long id, PostType type) {
    return Post.builder()
        .id(id)
        .userId(10L)
        .type(type)
        .title(type == PostType.QUESTION ? "question" : null)
        .content("content")
        .reward(type == PostType.QUESTION ? 100L : 0L)
        .status(PostStatus.OPEN)
        .createdAt(LocalDateTime.of(2026, 4, 20, 12, 0))
        .updatedAt(LocalDateTime.of(2026, 4, 20, 12, 0))
        .build();
  }

  private PostListResult result(Long postId) {
    return result(postId, PostType.FREE);
  }

  private PostListResult result(Long postId, PostType type) {
    LocalDateTime now = LocalDateTime.of(2026, 4, 20, 12, 0);
    return new PostListResult(
        postId,
        type,
        type == PostType.QUESTION ? "question" : null,
        "content",
        1L,
        0L,
        true,
        10L,
        "writer",
        null,
        type == PostType.QUESTION ? 100L : 0L,
        false,
        List.of(),
        List.of(),
        now,
        now);
  }
}
