package momzzangseven.mztkbe.modules.post.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.pagination.InvalidCursorException;
import momzzangseven.mztkbe.global.error.post.InvalidMyPostsQueryException;
import momzzangseven.mztkbe.global.pagination.CursorCodec;
import momzzangseven.mztkbe.modules.post.application.dto.GetMyPostsCursorCommand;
import momzzangseven.mztkbe.modules.post.application.dto.GetMyPostsCursorResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostListResult;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
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
@DisplayName("GetMyPostsCursorService unit test")
class GetMyPostsCursorServiceTest {

  @Mock private PostPersistencePort postPersistencePort;
  @Mock private LoadTagPort loadTagPort;
  @Mock private PostListEnricher postListEnricher;

  @InjectMocks private GetMyPostsCursorService service;

  @Test
  @DisplayName("execute returns empty result without enrichment when no authored posts")
  void execute_empty() {
    GetMyPostsCursorCommand command =
        new GetMyPostsCursorCommand(7L, PostType.FREE, null, null, null, 10);
    when(postPersistencePort.findPostsByAuthorCursor(
            7L, PostType.FREE, null, null, command.pageRequest()))
        .thenReturn(List.of());

    GetMyPostsCursorResult result = service.execute(command);

    assertThat(result.posts()).isEmpty();
    assertThat(result.hasNext()).isFalse();
    assertThat(result.nextCursor()).isNull();
    verifyNoInteractions(postListEnricher);
  }

  @Test
  @DisplayName("execute validates command before persistence")
  void execute_invalidCommandDoesNotQueryPersistence() {
    GetMyPostsCursorCommand command = new GetMyPostsCursorCommand(7L, null, null, null, null, 10);

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(InvalidMyPostsQueryException.class);

    verifyNoInteractions(postPersistencePort);
    verifyNoInteractions(loadTagPort);
    verifyNoInteractions(postListEnricher);
  }

  @Test
  @DisplayName("execute rejects invalid cursor before persistence")
  void execute_invalidCursorDoesNotQueryPersistence() {
    GetMyPostsCursorCommand command =
        new GetMyPostsCursorCommand(7L, PostType.FREE, null, null, "%%%", 10);

    assertThatThrownBy(() -> service.execute(command)).isInstanceOf(InvalidCursorException.class);

    verifyNoInteractions(postPersistencePort);
    verifyNoInteractions(loadTagPort);
    verifyNoInteractions(postListEnricher);
  }

  @Test
  @DisplayName("missing tag returns empty result without querying posts")
  void execute_missingTagDoesNotQueryPosts() {
    GetMyPostsCursorCommand command =
        new GetMyPostsCursorCommand(7L, PostType.QUESTION, " Squat ", null, null, 10);
    when(loadTagPort.findTagIdByName("squat")).thenReturn(Optional.empty());

    GetMyPostsCursorResult result = service.execute(command);

    assertThat(result.posts()).isEmpty();
    assertThat(result.hasNext()).isFalse();
    assertThat(result.nextCursor()).isNull();
    verifyNoInteractions(postPersistencePort);
    verifyNoInteractions(postListEnricher);
  }

  @Test
  @DisplayName("execute forwards normalized tag and effective search to persistence")
  void execute_forwardsNormalizedFilters() {
    GetMyPostsCursorCommand command =
        new GetMyPostsCursorCommand(7L, PostType.QUESTION, " Squat ", " Form ", null, 10);
    when(loadTagPort.findTagIdByName("squat")).thenReturn(Optional.of(11L));
    when(postPersistencePort.findPostsByAuthorCursor(
            7L, PostType.QUESTION, 11L, "form", command.pageRequest()))
        .thenReturn(List.of());

    service.execute(command);

    verify(postPersistencePort)
        .findPostsByAuthorCursor(7L, PostType.QUESTION, 11L, "form", command.pageRequest());
  }

  @Test
  @DisplayName("FREE search is ignored when querying persistence")
  void execute_freeSearchIgnored() {
    GetMyPostsCursorCommand command =
        new GetMyPostsCursorCommand(7L, PostType.FREE, null, " Form ", null, 10);
    when(postPersistencePort.findPostsByAuthorCursor(
            7L, PostType.FREE, null, null, command.pageRequest()))
        .thenReturn(List.of());

    service.execute(command);

    verify(postPersistencePort)
        .findPostsByAuthorCursor(7L, PostType.FREE, null, null, command.pageRequest());
  }

  @Test
  @DisplayName("execute trims probe row, enriches with requester id, and builds next cursor")
  void execute_trimsProbeAndBuildsNextCursor() {
    GetMyPostsCursorCommand command =
        new GetMyPostsCursorCommand(7L, PostType.FREE, null, null, null, 2);
    LocalDateTime firstCreatedAt = LocalDateTime.of(2026, 4, 27, 12, 0);
    LocalDateTime secondCreatedAt = LocalDateTime.of(2026, 4, 27, 11, 0);
    LocalDateTime probeCreatedAt = LocalDateTime.of(2026, 4, 27, 10, 0);
    Post first = post(1L, PostType.FREE, firstCreatedAt);
    Post second = post(2L, PostType.FREE, secondCreatedAt);
    Post probe = post(3L, PostType.FREE, probeCreatedAt);
    when(postPersistencePort.findPostsByAuthorCursor(
            7L, PostType.FREE, null, null, command.pageRequest()))
        .thenReturn(List.of(first, second, probe));
    when(postListEnricher.enrich(any(), any())).thenReturn(List.of(result(1L), result(2L)));

    GetMyPostsCursorResult result = service.execute(command);

    assertThat(result.posts()).extracting(PostListResult::postId).containsExactly(1L, 2L);
    assertThat(result.hasNext()).isTrue();
    assertThat(result.nextCursor()).isNotNull();
    assertThat(CursorCodec.decode(result.nextCursor(), command.pageRequest().scope()).id())
        .isEqualTo(2L);
    assertThat(CursorCodec.decode(result.nextCursor(), command.pageRequest().scope()).createdAt())
        .isEqualTo(secondCreatedAt);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Post>> postsCaptor = ArgumentCaptor.forClass(List.class);
    verify(postListEnricher).enrich(postsCaptor.capture(), org.mockito.Mockito.eq(7L));
    assertThat(postsCaptor.getValue().stream().map(Post::getId)).containsExactly(1L, 2L);
  }

  private Post post(Long id, PostType type, LocalDateTime createdAt) {
    return Post.builder()
        .id(id)
        .userId(7L)
        .type(type)
        .title(type == PostType.QUESTION ? "question" : null)
        .content("content")
        .reward(type == PostType.QUESTION ? 100L : 0L)
        .status(PostStatus.OPEN)
        .createdAt(createdAt)
        .updatedAt(createdAt)
        .build();
  }

  private PostListResult result(Long postId) {
    LocalDateTime now = LocalDateTime.of(2026, 4, 27, 12, 0);
    return new PostListResult(
        postId,
        PostType.FREE,
        null,
        "content",
        1L,
        0L,
        false,
        7L,
        "writer",
        null,
        0L,
        false,
        List.of(),
        List.of(),
        now,
        now);
  }
}
