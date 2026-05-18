package momzzangseven.mztkbe.modules.admin.board.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardPostSortKey;
import momzzangseven.mztkbe.modules.admin.board.application.dto.GetAdminBoardPostsCommand;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardPostCommentCountsPort;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardPostListPolicyPort;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardPostsPort;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardWriterNicknamesPort;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostModerationStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostPublicationStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetAdminBoardPostsService 단위 테스트")
class GetAdminBoardPostsServiceTest {

  @Mock private LoadAdminBoardPostsPort loadAdminBoardPostsPort;
  @Mock private LoadAdminBoardPostCommentCountsPort loadAdminBoardPostCommentCountsPort;
  @Mock private LoadAdminBoardWriterNicknamesPort loadAdminBoardWriterNicknamesPort;
  @Mock private LoadAdminBoardPostListPolicyPort loadAdminBoardPostListPolicyPort;

  @InjectMocks private GetAdminBoardPostsService service;

  @Test
  @DisplayName("게시글, 작성자 닉네임, 댓글 수를 조합하고 commentCount 로 정렬한다")
  void execute_combinesAndSortsByCommentCount() {
    GetAdminBoardPostsCommand command =
        new GetAdminBoardPostsCommand(
            9L,
            "hello",
            null,
            null,
            AdminBoardPostStatus.OPEN,
            AdminBoardPostType.QUESTION,
            AdminBoardPostPublicationStatus.FAILED,
            AdminBoardPostModerationStatus.BLOCKED,
            0,
            20,
            AdminBoardPostSortKey.COMMENT_COUNT);
    var postQuery =
        new LoadAdminBoardPostsPort.AdminBoardPostQuery(
            "hello",
            null,
            null,
            AdminBoardPostStatus.OPEN,
            AdminBoardPostType.QUESTION,
            AdminBoardPostPublicationStatus.FAILED,
            AdminBoardPostModerationStatus.BLOCKED);
    given(loadAdminBoardPostsPort.count(postQuery)).willReturn(2L);
    given(loadAdminBoardPostListPolicyPort.maxCommentCountSortScanSize()).willReturn(100);
    given(loadAdminBoardPostsPort.load(postQuery))
        .willReturn(
            List.of(
                post(10L, 1L, "short", LocalDateTime.parse("2025-01-01T00:00:00")),
                post(11L, 2L, "x".repeat(140), LocalDateTime.parse("2025-01-02T00:00:00"))));
    given(loadAdminBoardPostCommentCountsPort.load(List.of(10L, 11L)))
        .willReturn(Map.of(10L, 1L, 11L, 5L));
    given(loadAdminBoardWriterNicknamesPort.load(List.of(1L, 2L)))
        .willReturn(Map.of(1L, "alpha", 2L, "beta"));

    var result = service.execute(command);

    assertThat(result.getTotalElements()).isEqualTo(2);
    assertThat(result.getContent()).extracting(item -> item.postId()).containsExactly(11L, 10L);
    assertThat(result.getContent().get(0).writerNickname()).isEqualTo("beta");
    assertThat(result.getContent().get(0).commentCount()).isEqualTo(5L);
    assertThat(result.getContent().get(0).contentPreview()).hasSize(120);
    assertThat(result.getContent().get(0).publicationStatus())
        .isEqualTo(AdminBoardPostPublicationStatus.VISIBLE);
    assertThat(result.getContent().get(0).moderationStatus())
        .isEqualTo(AdminBoardPostModerationStatus.NORMAL);
    verify(loadAdminBoardPostsPort).count(postQuery);
  }

  @Test
  @DisplayName("contentPreview 는 이모지 surrogate pair 를 깨지 않고 code point 기준으로 자른다")
  void execute_previewTruncatesByCodePoint() {
    GetAdminBoardPostsCommand command =
        new GetAdminBoardPostsCommand(
            9L,
            "hello",
            null,
            null,
            AdminBoardPostStatus.OPEN,
            null,
            null,
            null,
            0,
            20,
            AdminBoardPostSortKey.COMMENT_COUNT);
    String content = "a".repeat(119) + "😀" + "b";
    var postQuery =
        new LoadAdminBoardPostsPort.AdminBoardPostQuery(
            "hello", null, null, AdminBoardPostStatus.OPEN, null, null, null);
    given(loadAdminBoardPostsPort.count(postQuery)).willReturn(1L);
    given(loadAdminBoardPostListPolicyPort.maxCommentCountSortScanSize()).willReturn(100);
    given(loadAdminBoardPostsPort.load(postQuery))
        .willReturn(List.of(post(10L, 1L, content, LocalDateTime.parse("2025-01-01T00:00:00"))));
    given(loadAdminBoardPostCommentCountsPort.load(List.of(10L))).willReturn(Map.of());
    given(loadAdminBoardWriterNicknamesPort.load(List.of(1L))).willReturn(Map.of(1L, "alpha"));

    var result = service.execute(command);

    String preview = result.getContent().get(0).contentPreview();
    assertThat(preview.codePointCount(0, preview.length())).isEqualTo(120);
    assertThat(preview).endsWith("😀");
  }

  @Test
  @DisplayName("post 기본 필드 sort 는 DB paging 결과에 대해서만 댓글 수와 닉네임을 병합한다")
  void execute_postFieldSort_usesPagedPostQuery() {
    GetAdminBoardPostsCommand command =
        new GetAdminBoardPostsCommand(
            9L,
            "hello",
            null,
            null,
            AdminBoardPostStatus.OPEN,
            AdminBoardPostType.FREE,
            AdminBoardPostPublicationStatus.VISIBLE,
            AdminBoardPostModerationStatus.NORMAL,
            1,
            2,
            AdminBoardPostSortKey.CREATED_AT);
    var first = post(12L, 3L, "first", LocalDateTime.parse("2025-01-03T00:00:00"));
    var second = post(11L, 2L, "second", LocalDateTime.parse("2025-01-02T00:00:00"));
    given(
            loadAdminBoardPostsPort.loadPage(
                new LoadAdminBoardPostsPort.AdminBoardPostPageQuery(
                    "hello",
                    null,
                    null,
                    AdminBoardPostStatus.OPEN,
                    AdminBoardPostType.FREE,
                    AdminBoardPostPublicationStatus.VISIBLE,
                    AdminBoardPostModerationStatus.NORMAL,
                    1,
                    2,
                    AdminBoardPostSortKey.CREATED_AT)))
        .willReturn(new PageImpl<>(List.of(first, second), PageRequest.of(1, 2), 5));
    given(loadAdminBoardPostCommentCountsPort.load(List.of(12L, 11L)))
        .willReturn(Map.of(12L, 2L, 11L, 1L));
    given(loadAdminBoardWriterNicknamesPort.load(List.of(3L, 2L)))
        .willReturn(Map.of(3L, "gamma", 2L, "beta"));

    var result = service.execute(command);

    assertThat(result.getTotalElements()).isEqualTo(5L);
    assertThat(result.getContent()).extracting(item -> item.postId()).containsExactly(12L, 11L);
    assertThat(result.getContent().get(0).writerNickname()).isEqualTo("gamma");
    assertThat(result.getContent().get(0).commentCount()).isEqualTo(2L);
    verify(loadAdminBoardPostsPort, never()).load(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("type sort 도 DB paging 경로를 사용한다")
  void execute_typeSort_usesPagedPostQuery() {
    GetAdminBoardPostsCommand command =
        new GetAdminBoardPostsCommand(
            9L, null, null, null, null, null, null, null, 0, 1, AdminBoardPostSortKey.TYPE);
    var post = post(10L, 1L, "short", LocalDateTime.parse("2025-01-01T00:00:00"));
    given(
            loadAdminBoardPostsPort.loadPage(
                new LoadAdminBoardPostsPort.AdminBoardPostPageQuery(
                    null, null, null, null, null, null, null, 0, 1, AdminBoardPostSortKey.TYPE)))
        .willReturn(new PageImpl<>(List.of(post), PageRequest.of(0, 1), 3));
    given(loadAdminBoardPostCommentCountsPort.load(List.of(10L))).willReturn(Map.of(10L, 4L));
    given(loadAdminBoardWriterNicknamesPort.load(List.of(1L))).willReturn(Map.of(1L, "alpha"));

    var result = service.execute(command);

    assertThat(result.getTotalElements()).isEqualTo(3L);
    assertThat(result.getContent()).extracting(item -> item.postId()).containsExactly(10L);
    verify(loadAdminBoardPostsPort, never()).load(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("postId sort 도 DB paging 경로를 사용한다")
  void execute_postIdSort_usesPagedPostQuery() {
    GetAdminBoardPostsCommand command =
        new GetAdminBoardPostsCommand(
            9L, null, null, null, null, null, null, null, 0, 1, AdminBoardPostSortKey.POST_ID);
    var post = post(12L, 1L, "short", LocalDateTime.parse("2025-01-01T00:00:00"));
    given(
            loadAdminBoardPostsPort.loadPage(
                new LoadAdminBoardPostsPort.AdminBoardPostPageQuery(
                    null, null, null, null, null, null, null, 0, 1, AdminBoardPostSortKey.POST_ID)))
        .willReturn(new PageImpl<>(List.of(post), PageRequest.of(0, 1), 3));
    given(loadAdminBoardPostCommentCountsPort.load(List.of(12L))).willReturn(Map.of(12L, 2L));
    given(loadAdminBoardWriterNicknamesPort.load(List.of(1L))).willReturn(Map.of(1L, "alpha"));

    var result = service.execute(command);

    assertThat(result.getTotalElements()).isEqualTo(3L);
    assertThat(result.getContent()).extracting(item -> item.postId()).containsExactly(12L);
    verify(loadAdminBoardPostsPort, never()).load(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("status sort 도 DB paging 경로를 사용한다")
  void execute_statusSort_usesPagedPostQuery() {
    GetAdminBoardPostsCommand command =
        new GetAdminBoardPostsCommand(
            9L, null, null, null, null, null, null, null, 0, 1, AdminBoardPostSortKey.STATUS);
    var post = post(10L, 1L, "short", LocalDateTime.parse("2025-01-01T00:00:00"));
    given(
            loadAdminBoardPostsPort.loadPage(
                new LoadAdminBoardPostsPort.AdminBoardPostPageQuery(
                    null, null, null, null, null, null, null, 0, 1, AdminBoardPostSortKey.STATUS)))
        .willReturn(new PageImpl<>(List.of(post), PageRequest.of(0, 1), 3));
    given(loadAdminBoardPostCommentCountsPort.load(List.of(10L))).willReturn(Map.of(10L, 1L));
    given(loadAdminBoardWriterNicknamesPort.load(List.of(1L))).willReturn(Map.of(1L, "alpha"));

    var result = service.execute(command);

    assertThat(result.getTotalElements()).isEqualTo(3L);
    assertThat(result.getContent()).extracting(item -> item.postId()).containsExactly(10L);
    verify(loadAdminBoardPostsPort, never()).load(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("commentCount sort 의 메모리 페이징은 정상 page 를 반환한다")
  void execute_commentCountSortMemoryPagination_returnsRequestedPage() {
    GetAdminBoardPostsCommand command =
        new GetAdminBoardPostsCommand(
            9L,
            null,
            null,
            null,
            AdminBoardPostStatus.OPEN,
            null,
            null,
            null,
            1,
            1,
            AdminBoardPostSortKey.COMMENT_COUNT);
    var postQuery =
        new LoadAdminBoardPostsPort.AdminBoardPostQuery(
            null, null, null, AdminBoardPostStatus.OPEN, null, null, null);
    given(loadAdminBoardPostsPort.count(postQuery)).willReturn(2L);
    given(loadAdminBoardPostListPolicyPort.maxCommentCountSortScanSize()).willReturn(100);
    given(loadAdminBoardPostsPort.load(postQuery))
        .willReturn(
            List.of(
                post(10L, 1L, "short", LocalDateTime.parse("2025-01-01T00:00:00")),
                post(11L, 2L, "short", LocalDateTime.parse("2025-01-02T00:00:00"))));
    given(loadAdminBoardPostCommentCountsPort.load(List.of(10L, 11L)))
        .willReturn(Map.of(10L, 1L, 11L, 5L));
    given(loadAdminBoardWriterNicknamesPort.load(List.of(1L, 2L)))
        .willReturn(Map.of(1L, "alpha", 2L, "beta"));

    var result = service.execute(command);

    assertThat(result.getTotalElements()).isEqualTo(2L);
    assertThat(result.getContent()).extracting(item -> item.postId()).containsExactly(10L);
  }

  @Test
  @DisplayName("commentCount sort 의 큰 page 는 int overflow 없이 빈 페이지를 반환한다")
  void execute_commentCountSortMemoryPagination_largePageReturnsEmptyPage() {
    GetAdminBoardPostsCommand command =
        new GetAdminBoardPostsCommand(
            9L,
            null,
            null,
            null,
            AdminBoardPostStatus.OPEN,
            null,
            null,
            null,
            Integer.MAX_VALUE,
            100,
            AdminBoardPostSortKey.COMMENT_COUNT);
    var postQuery =
        new LoadAdminBoardPostsPort.AdminBoardPostQuery(
            null, null, null, AdminBoardPostStatus.OPEN, null, null, null);
    given(loadAdminBoardPostsPort.count(postQuery)).willReturn(2L);
    given(loadAdminBoardPostListPolicyPort.maxCommentCountSortScanSize()).willReturn(100);
    given(loadAdminBoardPostsPort.load(postQuery))
        .willReturn(
            List.of(
                post(10L, 1L, "short", LocalDateTime.parse("2025-01-01T00:00:00")),
                post(11L, 2L, "short", LocalDateTime.parse("2025-01-02T00:00:00"))));
    given(loadAdminBoardPostCommentCountsPort.load(List.of(10L, 11L)))
        .willReturn(Map.of(10L, 1L, 11L, 5L));
    given(loadAdminBoardWriterNicknamesPort.load(List.of(1L, 2L)))
        .willReturn(Map.of(1L, "alpha", 2L, "beta"));

    var result = service.execute(command);

    assertThat(result.getTotalElements()).isEqualTo(2L);
    assertThat(result.getContent()).isEmpty();
  }

  @Test
  @DisplayName("commentCount sort 는 matching post 수가 scan 상한을 넘으면 전체 load 전에 거부한다")
  void execute_commentCountSortRejectsTooBroadScanBeforeLoadingAllPosts() {
    GetAdminBoardPostsCommand command =
        new GetAdminBoardPostsCommand(
            9L,
            null,
            null,
            null,
            AdminBoardPostStatus.OPEN,
            null,
            null,
            null,
            0,
            20,
            AdminBoardPostSortKey.COMMENT_COUNT);
    var postQuery =
        new LoadAdminBoardPostsPort.AdminBoardPostQuery(
            null, null, null, AdminBoardPostStatus.OPEN, null, null, null);
    given(loadAdminBoardPostsPort.count(postQuery)).willReturn(3L);
    given(loadAdminBoardPostListPolicyPort.maxCommentCountSortScanSize()).willReturn(2);

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("commentCount sort can scan at most 2 matching posts");

    verify(loadAdminBoardPostsPort, never()).load(org.mockito.ArgumentMatchers.any());
    verify(loadAdminBoardPostCommentCountsPort, never()).load(org.mockito.ArgumentMatchers.any());
    verify(loadAdminBoardWriterNicknamesPort, never()).load(org.mockito.ArgumentMatchers.any());
  }

  private LoadAdminBoardPostsPort.AdminBoardPostView post(
      Long postId, Long writerId, String content, LocalDateTime createdAt) {
    return new LoadAdminBoardPostsPort.AdminBoardPostView(
        postId,
        AdminBoardPostType.FREE,
        AdminBoardPostStatus.OPEN,
        AdminBoardPostPublicationStatus.VISIBLE,
        AdminBoardPostModerationStatus.NORMAL,
        "title-" + postId,
        content,
        writerId,
        createdAt);
  }
}
