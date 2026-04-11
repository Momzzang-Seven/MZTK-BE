package momzzangseven.mztkbe.modules.post.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

  @InjectMocks private MarkQuestionPostSolvedService markQuestionPostSolvedService;

  @Test
  @DisplayName("returns conditional resolve update count")
  void executeReturnsUpdatedCount() {
    when(postPersistencePort.markQuestionPostSolved(1L)).thenReturn(1);

    int updated = markQuestionPostSolvedService.execute(1L);

    assertThat(updated).isEqualTo(1);
    verify(postPersistencePort).markQuestionPostSolved(1L);
  }

  @Test
  @DisplayName("returns zero when bulk resolve update becomes no-op")
  void executeReturnsZeroWhenAlreadySolvedOrNotQuestion() {
    when(postPersistencePort.markQuestionPostSolved(2L)).thenReturn(0);

    int updated = markQuestionPostSolvedService.execute(2L);

    assertThat(updated).isZero();
    verify(postPersistencePort).markQuestionPostSolved(2L);
  }
}
