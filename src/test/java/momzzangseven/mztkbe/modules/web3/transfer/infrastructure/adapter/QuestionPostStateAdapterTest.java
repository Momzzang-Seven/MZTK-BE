package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.post.application.port.in.MarkQuestionPostSolvedUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuestionPostStateAdapter")
class QuestionPostStateAdapterTest {

  @Mock private MarkQuestionPostSolvedUseCase markQuestionPostSolvedUseCase;

  @InjectMocks private QuestionPostStateAdapter adapter;

  @Test
  @DisplayName("delegates markSolved to post use case")
  void markSolved_delegatesToUseCase() {
    when(markQuestionPostSolvedUseCase.execute(77L)).thenReturn(1);

    int updated = adapter.markSolved(77L);

    assertThat(updated).isEqualTo(1);
    verify(markQuestionPostSolvedUseCase).execute(77L);
  }
}
