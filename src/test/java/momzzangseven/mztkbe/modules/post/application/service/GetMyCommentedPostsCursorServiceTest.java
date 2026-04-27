package momzzangseven.mztkbe.modules.post.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.global.pagination.CursorCodec;
import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.modules.post.application.dto.GetMyCommentedPostsCursorCommand;
import momzzangseven.mztkbe.modules.post.application.dto.GetMyCommentedPostsCursorResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostListResult;
import momzzangseven.mztkbe.modules.post.application.port.out.CommentedPostRef;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadCommentedPostRefsPort;
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
@DisplayName("GetMyCommentedPostsCursorService unit test")
class GetMyCommentedPostsCursorServiceTest {

  @Mock private LoadCommentedPostRefsPort loadCommentedPostRefsPort;
  @Mock private PostPersistencePort postPersistencePort;
  @Mock private PostListEnricher postListEnricher;

  @InjectMocks private GetMyCommentedPostsCursorService service;

  @Test
  @DisplayName("loads size+1 refs, enriches page posts, and builds next cursor from last page ref")
  void execute_trimsProbeAndBuildsCursor() {
    LocalDateTime base = LocalDateTime.of(2026, 4, 26, 12, 0);
    GetMyCommentedPostsCursorCommand command =
        new GetMyCommentedPostsCursorCommand(10L, PostType.FREE, null, 2);
    CommentedPostRef first = new CommentedPostRef(100L, 1000L, base);
    CommentedPostRef second = new CommentedPostRef(200L, 900L, base.minusMinutes(1));
    CommentedPostRef probe = new CommentedPostRef(300L, 800L, base.minusMinutes(2));
    Post firstPost = post(100L, PostType.FREE);
    Post secondPost = post(200L, PostType.FREE);
    PostListResult firstResult = result(100L, PostType.FREE, base);
    PostListResult secondResult = result(200L, PostType.FREE, base.minusMinutes(1));

    given(loadCommentedPostRefsPort.loadCommentedPostRefs(eq(10L), eq(PostType.FREE), any()))
        .willReturn(List.of(first, second, probe));
    given(postPersistencePort.loadPostsByIdsPreservingOrder(List.of(100L, 200L)))
        .willReturn(List.of(firstPost, secondPost));
    given(postListEnricher.enrich(List.of(firstPost, secondPost), 10L))
        .willReturn(List.of(firstResult, secondResult));

    GetMyCommentedPostsCursorResult result = service.execute(command);

    assertThat(result.hasNext()).isTrue();
    assertThat(result.posts()).extracting(PostListResult::postId).containsExactly(100L, 200L);
    assertThat(result.nextCursor()).isNotNull();
    assertThat(CursorCodec.decode(result.nextCursor(), command.pageRequest().scope()).id())
        .isEqualTo(900L);

    ArgumentCaptor<CursorPageRequest> pageRequestCaptor =
        ArgumentCaptor.forClass(CursorPageRequest.class);
    verify(loadCommentedPostRefsPort)
        .loadCommentedPostRefs(eq(10L), eq(PostType.FREE), pageRequestCaptor.capture());
    assertThat(pageRequestCaptor.getValue().limitWithProbe()).isEqualTo(3);
  }

  @Test
  @DisplayName("returns empty without post load when there are no comment refs")
  void execute_emptyRefs_returnsEmpty() {
    GetMyCommentedPostsCursorCommand command =
        new GetMyCommentedPostsCursorCommand(10L, PostType.QUESTION, null, 10);
    given(loadCommentedPostRefsPort.loadCommentedPostRefs(eq(10L), eq(PostType.QUESTION), any()))
        .willReturn(List.of());

    GetMyCommentedPostsCursorResult result = service.execute(command);

    assertThat(result.posts()).isEmpty();
    assertThat(result.hasNext()).isFalse();
    assertThat(result.nextCursor()).isNull();
    verify(postPersistencePort, never()).loadPostsByIdsPreservingOrder(any());
    verify(postListEnricher, never()).enrich(any(), any());
  }

  private Post post(Long id, PostType type) {
    return Post.builder()
        .id(id)
        .userId(1L)
        .type(type)
        .title(type == PostType.QUESTION ? "question" : null)
        .content("content")
        .reward(type == PostType.QUESTION ? 100L : 0L)
        .status(PostStatus.OPEN)
        .build();
  }

  private PostListResult result(Long id, PostType type, LocalDateTime createdAt) {
    return new PostListResult(
        id,
        type,
        type == PostType.QUESTION ? "question" : null,
        "content",
        0L,
        0L,
        false,
        1L,
        null,
        null,
        type == PostType.QUESTION ? 100L : 0L,
        false,
        List.of(),
        List.of(),
        createdAt,
        createdAt);
  }
}
