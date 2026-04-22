package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetAnswerSummaryForUpdateUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetAnswerSummaryUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.GetPostContextUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QnaAcceptContextAdapterTest {

  @Mock private GetPostContextUseCase getPostContextUseCase;
  @Mock private GetAnswerSummaryForUpdateUseCase getAnswerSummaryForUpdateUseCase;

  private QnaAcceptContextAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new QnaAcceptContextAdapter(getPostContextUseCase, getAnswerSummaryForUpdateUseCase);
  }

  @Test
  void loadForUpdate_locksAnswerThenPostAndMapsContext() {
    when(getAnswerSummaryForUpdateUseCase.getAnswerSummaryForUpdate(201L))
        .thenReturn(Optional.of(new GetAnswerSummaryUseCase.AnswerSummary(201L, 101L, 22L, "답변")));
    when(getPostContextUseCase.getPostContextForUpdate(101L))
        .thenReturn(
            Optional.of(
                new GetPostContextUseCase.PostContext(101L, 7L, false, true, "질문", 50L, false)));

    var result = adapter.loadForUpdate(101L, 201L);

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().postId()).isEqualTo(101L);
    assertThat(result.orElseThrow().answerId()).isEqualTo(201L);
    assertThat(result.orElseThrow().requesterUserId()).isEqualTo(7L);
    assertThat(result.orElseThrow().answerWriterUserId()).isEqualTo(22L);
    assertThat(result.orElseThrow().questionContent()).isEqualTo("질문");
    assertThat(result.orElseThrow().answerContent()).isEqualTo("답변");
    verify(getPostContextUseCase, never()).getPostContext(101L);
    InOrder inOrder = inOrder(getAnswerSummaryForUpdateUseCase, getPostContextUseCase);
    inOrder.verify(getAnswerSummaryForUpdateUseCase).getAnswerSummaryForUpdate(201L);
    inOrder.verify(getPostContextUseCase).getPostContextForUpdate(101L);
  }

  @Test
  void loadForUpdate_returnsEmptyWhenAnswerPostMismatch() {
    when(getAnswerSummaryForUpdateUseCase.getAnswerSummaryForUpdate(201L))
        .thenReturn(Optional.of(new GetAnswerSummaryUseCase.AnswerSummary(201L, 999L, 22L, "답변")));
    when(getPostContextUseCase.getPostContextForUpdate(101L))
        .thenReturn(
            Optional.of(
                new GetPostContextUseCase.PostContext(101L, 7L, false, true, "질문", 50L, false)));

    var result = adapter.loadForUpdate(101L, 201L);

    assertThat(result).isEmpty();
  }
}
