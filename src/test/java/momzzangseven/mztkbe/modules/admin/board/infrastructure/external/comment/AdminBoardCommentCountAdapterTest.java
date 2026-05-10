package momzzangseven.mztkbe.modules.admin.board.infrastructure.external.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.comment.application.port.in.CountManagedBoardPostCommentsUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminBoardCommentCountAdapter unit test")
class AdminBoardCommentCountAdapterTest {

  @Mock private CountManagedBoardPostCommentsUseCase countManagedBoardPostCommentsUseCase;

  @InjectMocks private AdminBoardCommentCountAdapter adapter;

  @Test
  @DisplayName("load() uses managed board comment count use case")
  void load_usesManagedBoardCountUseCase() {
    when(countManagedBoardPostCommentsUseCase.countByPostIds(List.of(10L)))
        .thenReturn(Map.of(10L, 3L));

    Map<Long, Long> result = adapter.load(List.of(10L));

    assertThat(result).containsEntry(10L, 3L);
    verify(countManagedBoardPostCommentsUseCase).countByPostIds(List.of(10L));
  }
}
