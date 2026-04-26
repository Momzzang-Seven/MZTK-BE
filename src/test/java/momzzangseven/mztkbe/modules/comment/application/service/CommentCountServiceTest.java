package momzzangseven.mztkbe.modules.comment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadCommentPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommentCountService unit test")
class CommentCountServiceTest {

  @Mock private LoadCommentPort loadCommentPort;

  @InjectMocks private CommentCountService commentCountService;

  @Test
  @DisplayName("countCommentsByPostIds() delegates to load comment port")
  void countCommentsByPostIds_delegatesToPort() {
    when(loadCommentPort.countCommentsByPostIds(List.of(1L, 2L))).thenReturn(Map.of(1L, 3L));

    Map<Long, Long> result = commentCountService.countCommentsByPostIds(List.of(1L, 2L));

    assertThat(result).containsEntry(1L, 3L);
    verify(loadCommentPort).countCommentsByPostIds(List.of(1L, 2L));
  }

  @Test
  @DisplayName("countCommentsByPostId() delegates to load comment port")
  void countCommentsByPostId_delegatesToPort() {
    when(loadCommentPort.countCommentsByPostId(1L)).thenReturn(3L);

    long result = commentCountService.countCommentsByPostId(1L);

    assertThat(result).isEqualTo(3L);
    verify(loadCommentPort).countCommentsByPostId(1L);
  }
}
