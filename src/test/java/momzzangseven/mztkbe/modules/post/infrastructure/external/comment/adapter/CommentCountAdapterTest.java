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
  @DisplayName("countCommentsByPostId() delegates to comment use case")
  void countCommentsByPostId_delegatesToUseCase() {
    when(countCommentsUseCase.countCommentsByPostId(10L)).thenReturn(3L);

    long result = commentCountAdapter.countCommentsByPostId(10L);

    assertThat(result).isEqualTo(3L);
    verify(countCommentsUseCase).countCommentsByPostId(10L);
  }

  @Test
  @DisplayName("countCommentsByPostIds() delegates batch counting to comment use case")
  void countCommentsByPostIds_delegatesToUseCase() {
    when(countCommentsUseCase.countCommentsByPostIds(List.of(10L, 11L)))
        .thenReturn(Map.of(10L, 2L, 11L, 0L));

    Map<Long, Long> result = commentCountAdapter.countCommentsByPostIds(List.of(10L, 11L));

    assertThat(result).containsEntry(10L, 2L).containsEntry(11L, 0L);
    verify(countCommentsUseCase).countCommentsByPostIds(List.of(10L, 11L));
  }
}
