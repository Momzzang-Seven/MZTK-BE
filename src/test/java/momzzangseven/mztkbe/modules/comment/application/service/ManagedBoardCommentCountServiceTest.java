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
@DisplayName("ManagedBoardCommentCountService unit test")
class ManagedBoardCommentCountServiceTest {

  @Mock private LoadCommentPort loadCommentPort;

  @InjectMocks private ManagedBoardCommentCountService service;

  @Test
  @DisplayName("countByPostIds() delegates to managed board comment count port")
  void countByPostIds_delegatesToPort() {
    when(loadCommentPort.countManagedBoardCommentsByPostIds(List.of(1L, 2L)))
        .thenReturn(Map.of(1L, 3L));

    Map<Long, Long> result = service.countByPostIds(List.of(1L, 2L));

    assertThat(result).containsEntry(1L, 3L);
    verify(loadCommentPort).countManagedBoardCommentsByPostIds(List.of(1L, 2L));
  }
}
