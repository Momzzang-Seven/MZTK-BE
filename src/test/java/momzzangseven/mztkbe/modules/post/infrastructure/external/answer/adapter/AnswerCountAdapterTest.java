package momzzangseven.mztkbe.modules.post.infrastructure.external.answer.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.answer.application.port.in.CountAnswersUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnswerCountAdapter unit test")
class AnswerCountAdapterTest {

  @Mock private CountAnswersUseCase countAnswersUseCase;

  @InjectMocks private AnswerCountAdapter answerCountAdapter;

  @Test
  @DisplayName("delegates answer count lookup to answer module use case")
  void countAnswers_delegatesToUseCase() {
    when(countAnswersUseCase.countAnswers(10L)).thenReturn(2L);

    long result = answerCountAdapter.countAnswers(10L);

    assertThat(result).isEqualTo(2L);
    verify(countAnswersUseCase).countAnswers(10L);
  }
}
