package momzzangseven.mztkbe.modules.answer.infrastructure.external.comment.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.answer.application.port.out.CountAnswerCommentsPort;
import momzzangseven.mztkbe.modules.comment.application.port.in.CountCommentsUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnswerCommentCountAdapter unit test")
class AnswerCommentCountAdapterTest {

  @Mock private CountCommentsUseCase countCommentsUseCase;

  @InjectMocks private AnswerCommentCountAdapter adapter;

  @Test
  @DisplayName("implements answer module output port for comment count lookup")
  void adapter_implementsAnswerOutputPort() {
    assertThat(adapter).isInstanceOf(CountAnswerCommentsPort.class);
  }

  @Test
  @DisplayName("countCommentsByAnswerIds() delegates only to comment module input port")
  void countCommentsByAnswerIds_delegatesToCommentUseCase() {
    List<Long> answerIds = List.of(10L, 20L);
    Map<Long, Long> counts = Map.of(10L, 3L, 20L, 1L);
    when(countCommentsUseCase.countCommentsByAnswerIds(answerIds)).thenReturn(counts);

    Map<Long, Long> result = adapter.countCommentsByAnswerIds(answerIds);

    assertThat(result).containsExactlyInAnyOrderEntriesOf(counts);
    verify(countCommentsUseCase).countCommentsByAnswerIds(answerIds);
  }
}
