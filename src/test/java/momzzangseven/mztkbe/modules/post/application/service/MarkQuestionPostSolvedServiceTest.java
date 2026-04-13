package momzzangseven.mztkbe.modules.post.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.post.application.dto.MarkQuestionPostSolvedCommand;
import momzzangseven.mztkbe.modules.post.application.port.out.CountAnswersPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MarkQuestionPostSolvedService unit test")
class MarkQuestionPostSolvedServiceTest {

  @Mock private PostPersistencePort postPersistencePort;
  @Mock private CountAnswersPort countAnswersPort;

  @InjectMocks private MarkQuestionPostSolvedService markQuestionPostSolvedService;

  @Test
  @DisplayName("returns repository update count when answers exist")
  void executeReturnsUpdatedCount() {
    MarkQuestionPostSolvedCommand command = new MarkQuestionPostSolvedCommand(1L);
    when(countAnswersPort.countAnswers(1L)).thenReturn(5L);
    when(postPersistencePort.markQuestionPostSolved(1L)).thenReturn(1);

    int updated = markQuestionPostSolvedService.execute(command);

    assertThat(updated).isEqualTo(1);
    verify(countAnswersPort).countAnswers(1L);
    verify(postPersistencePort).markQuestionPostSolved(1L);
  }

  @Test
  @DisplayName("returns zero and bypasses update when no answers exist")
  void executeReturnsZeroWhenNoAnswers() {
    MarkQuestionPostSolvedCommand command = new MarkQuestionPostSolvedCommand(1L);
    when(countAnswersPort.countAnswers(1L)).thenReturn(0L);

    int updated = markQuestionPostSolvedService.execute(command);

    assertThat(updated).isZero();
    verify(countAnswersPort).countAnswers(1L);
    verifyNoInteractions(postPersistencePort);
  }

  @Test
  @DisplayName("returns zero when repository update returns zero even with answers")
  void executeReturnsZeroWhenAlreadySolvedOrNotQuestion() {
    MarkQuestionPostSolvedCommand command = new MarkQuestionPostSolvedCommand(2L);
    when(countAnswersPort.countAnswers(2L)).thenReturn(1L);
    when(postPersistencePort.markQuestionPostSolved(2L)).thenReturn(0);

    int updated = markQuestionPostSolvedService.execute(command);

    assertThat(updated).isZero();
    verify(countAnswersPort).countAnswers(2L);
    verify(postPersistencePort).markQuestionPostSolved(2L);
  }
}
