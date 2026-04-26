package momzzangseven.mztkbe.modules.post.infrastructure.external.comment.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.comment.application.port.in.CountCommentsUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommentCountAdapter unit test")
class CommentCountAdapterTest {

  @Mock private CountCommentsUseCase countCommentsUseCase;

  @InjectMocks private CommentCountAdapter commentCountAdapter;

  @Test
  @DisplayName("countCommentsByPostIds() delegates to comment module use case")
  void countCommentsByPostIds_delegatesToUseCase() {
    when(countCommentsUseCase.countCommentsByPostIds(List.of(1L, 2L))).thenReturn(Map.of(1L, 3L));

    Map<Long, Long> result = commentCountAdapter.countCommentsByPostIds(List.of(1L, 2L));

    assertThat(result).containsEntry(1L, 3L);
    verify(countCommentsUseCase).countCommentsByPostIds(List.of(1L, 2L));
  }

  @Test
  @DisplayName("countCommentsByPostId() delegates to comment module use case")
  void countCommentsByPostId_delegatesToUseCase() {
    when(countCommentsUseCase.countCommentsByPostId(1L)).thenReturn(3L);

    long result = commentCountAdapter.countCommentsByPostId(1L);

    assertThat(result).isEqualTo(3L);
    verify(countCommentsUseCase).countCommentsByPostId(1L);
  }
}
