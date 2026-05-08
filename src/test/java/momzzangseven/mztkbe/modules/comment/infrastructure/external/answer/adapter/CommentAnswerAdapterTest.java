package momzzangseven.mztkbe.modules.comment.infrastructure.external.answer.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetAnswerSummaryForUpdateUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetAnswerSummaryUseCase;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadAnswerPort.AnswerCommentContext;
import momzzangseven.mztkbe.modules.post.application.port.in.GetPostContextUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommentAnswerAdapter unit test")
class CommentAnswerAdapterTest {

  @Mock private GetAnswerSummaryUseCase getAnswerSummaryUseCase;
  @Mock private GetAnswerSummaryForUpdateUseCase getAnswerSummaryForUpdateUseCase;
  @Mock private GetPostContextUseCase getPostContextUseCase;

  @InjectMocks private CommentAnswerAdapter adapter;

  @Test
  @DisplayName("implements comment module output port for answer context lookup")
  void adapter_implementsCommentOutputPort() {
    assertThat(adapter).isInstanceOf(LoadAnswerPort.class);
  }

  @Test
  @DisplayName("loadAnswerCommentContext() uses non-locking answer summary for reads")
  void loadAnswerCommentContext_usesNonLockingSummary() {
    given(getAnswerSummaryUseCase.getAnswerSummary(300L))
        .willReturn(Optional.of(new GetAnswerSummaryUseCase.AnswerSummary(300L, 100L, 200L)));
    given(getPostContextUseCase.getPostContext(100L))
        .willReturn(Optional.of(new GetPostContextUseCase.PostContext(100L, 200L, false, true)));

    Optional<AnswerCommentContext> result = adapter.loadAnswerCommentContext(300L);

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().answerId()).isEqualTo(300L);
    assertThat(result.orElseThrow().postId()).isEqualTo(100L);
    assertThat(result.orElseThrow().answerLocked()).isFalse();
    verify(getAnswerSummaryUseCase).getAnswerSummary(300L);
    verify(getPostContextUseCase).getPostContext(100L);
    verifyNoInteractions(getAnswerSummaryForUpdateUseCase);
  }

  @Test
  @DisplayName("loadAnswerCommentContextForUpdate() maps root post answer lock")
  void loadAnswerCommentContextForUpdate_mapsRootPostAnswerLock() {
    given(getAnswerSummaryForUpdateUseCase.getAnswerSummaryForUpdate(300L))
        .willReturn(
            Optional.of(
                new GetAnswerSummaryUseCase.AnswerSummary(
                    300L, 100L, 200L, "accepted answer", true)));
    given(getPostContextUseCase.getPostContext(100L))
        .willReturn(
            Optional.of(
                new GetPostContextUseCase.PostContext(
                    100L, 200L, true, true, "question", 100L, true)));

    Optional<AnswerCommentContext> result = adapter.loadAnswerCommentContextForUpdate(300L);

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().answerId()).isEqualTo(300L);
    assertThat(result.orElseThrow().postId()).isEqualTo(100L);
    assertThat(result.orElseThrow().answerLocked()).isTrue();
    verify(getAnswerSummaryForUpdateUseCase).getAnswerSummaryForUpdate(300L);
    verify(getPostContextUseCase).getPostContext(100L);
    verifyNoInteractions(getAnswerSummaryUseCase);
  }

  @Test
  @DisplayName("loadAnswerCommentContext() maps missing answer summary to empty context")
  void loadAnswerCommentContext_returnsEmptyWhenSummaryMissing() {
    given(getAnswerSummaryUseCase.getAnswerSummary(300L)).willReturn(Optional.empty());

    Optional<AnswerCommentContext> result = adapter.loadAnswerCommentContext(300L);

    assertThat(result).isEmpty();
    verify(getAnswerSummaryUseCase).getAnswerSummary(300L);
    verifyNoInteractions(getPostContextUseCase);
    verifyNoInteractions(getAnswerSummaryForUpdateUseCase);
  }

  @Test
  @DisplayName("loadAnswerCommentContextForUpdate() maps missing answer summary to empty context")
  void loadAnswerCommentContextForUpdate_returnsEmptyWhenSummaryMissing() {
    given(getAnswerSummaryForUpdateUseCase.getAnswerSummaryForUpdate(300L))
        .willReturn(Optional.empty());

    Optional<AnswerCommentContext> result = adapter.loadAnswerCommentContextForUpdate(300L);

    assertThat(result).isEmpty();
    verify(getAnswerSummaryForUpdateUseCase).getAnswerSummaryForUpdate(300L);
    verifyNoInteractions(getPostContextUseCase);
    verifyNoInteractions(getAnswerSummaryUseCase);
  }
}
