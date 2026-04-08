package momzzangseven.mztkbe.modules.post.infrastructure.external.answer.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetAnswerSummaryUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadAnswerLikeTargetPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnswerLikeTargetAdapter unit test")
class AnswerLikeTargetAdapterTest {

  @Mock private GetAnswerSummaryUseCase getAnswerSummaryUseCase;

  @InjectMocks private AnswerLikeTargetAdapter answerLikeTargetAdapter;

  @Test
  @DisplayName("maps answer summary to answer like target")
  void loadAnswerTarget_mapsAnswerSummary() {
    when(getAnswerSummaryUseCase.getAnswerSummary(20L))
        .thenReturn(Optional.of(new GetAnswerSummaryUseCase.AnswerSummary(20L, 10L, 2L)));

    Optional<LoadAnswerLikeTargetPort.AnswerLikeTarget> result =
        answerLikeTargetAdapter.loadAnswerTarget(20L);

    assertThat(result).isPresent();
    assertThat(result.get().answerId()).isEqualTo(20L);
    assertThat(result.get().postId()).isEqualTo(10L);
  }

  @Test
  @DisplayName("returns empty when answer summary is missing")
  void loadAnswerTarget_returnsEmptyWhenMissing() {
    when(getAnswerSummaryUseCase.getAnswerSummary(20L)).thenReturn(Optional.empty());

    Optional<LoadAnswerLikeTargetPort.AnswerLikeTarget> result =
        answerLikeTargetAdapter.loadAnswerTarget(20L);

    assertThat(result).isEmpty();
  }
}
