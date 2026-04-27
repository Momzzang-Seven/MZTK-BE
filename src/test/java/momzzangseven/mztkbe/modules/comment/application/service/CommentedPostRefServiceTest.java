package momzzangseven.mztkbe.modules.comment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.modules.comment.application.dto.FindCommentedPostRefsQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.LatestCommentedPostRef;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadCommentPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommentedPostRefService unit test")
class CommentedPostRefServiceTest {

  @Mock private LoadCommentPort loadCommentPort;

  @InjectMocks private CommentedPostRefService service;

  @Test
  @DisplayName("validates query and delegates to LoadCommentPort")
  void execute_delegatesToPort() {
    CursorPageRequest pageRequest = new CursorPageRequest(null, 10, "scope");
    FindCommentedPostRefsQuery query = new FindCommentedPostRefsQuery(1L, "free", pageRequest);
    LatestCommentedPostRef ref =
        new LatestCommentedPostRef(100L, 10L, LocalDateTime.of(2026, 4, 26, 12, 0));
    given(loadCommentPort.findCommentedPostRefsByUserCursor(query)).willReturn(List.of(ref));

    List<LatestCommentedPostRef> result = service.execute(query);

    assertThat(result).containsExactly(ref);
    verify(loadCommentPort).findCommentedPostRefsByUserCursor(query);
  }
}
