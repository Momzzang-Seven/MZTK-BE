package momzzangseven.mztkbe.modules.admin.board.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardPostSortKey;
import momzzangseven.mztkbe.modules.admin.board.application.dto.GetAdminBoardPostsCommand;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardPostCommentCountsPort;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardPostsPort;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardWriterNicknamesPort;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetAdminBoardPostsService 단위 테스트")
class GetAdminBoardPostsServiceTest {

  @Mock private LoadAdminBoardPostsPort loadAdminBoardPostsPort;
  @Mock private LoadAdminBoardPostCommentCountsPort loadAdminBoardPostCommentCountsPort;
  @Mock private LoadAdminBoardWriterNicknamesPort loadAdminBoardWriterNicknamesPort;

  @InjectMocks private GetAdminBoardPostsService service;

  @Test
  @DisplayName("게시글, 작성자 닉네임, 댓글 수를 조합하고 commentCount 로 정렬한다")
  void execute_combinesAndSortsByCommentCount() {
    GetAdminBoardPostsCommand command =
        new GetAdminBoardPostsCommand(
            9L, "hello", PostStatus.OPEN, 0, 20, AdminBoardPostSortKey.COMMENT_COUNT);
    given(
            loadAdminBoardPostsPort.load(
                new LoadAdminBoardPostsPort.AdminBoardPostQuery("hello", PostStatus.OPEN)))
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
  }

  private LoadAdminBoardPostsPort.AdminBoardPostView post(
      Long postId, Long writerId, String content, LocalDateTime createdAt) {
    return new LoadAdminBoardPostsPort.AdminBoardPostView(
        postId, PostType.FREE, PostStatus.OPEN, "title-" + postId, content, writerId, createdAt);
  }
}
