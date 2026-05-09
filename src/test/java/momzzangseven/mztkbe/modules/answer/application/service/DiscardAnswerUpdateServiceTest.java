package momzzangseven.mztkbe.modules.answer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.answer.AnswerPublicationStateException;
import momzzangseven.mztkbe.modules.answer.application.dto.DiscardAnswerUpdateCommand;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerUpdateImagePort;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerUpdateStatePort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;
import momzzangseven.mztkbe.modules.answer.domain.vo.AnswerUpdateStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DiscardAnswerUpdateService")
class DiscardAnswerUpdateServiceTest {

  @Mock private LoadAnswerPort loadAnswerPort;
  @Mock private AnswerUpdateStatePort answerUpdateStatePort;
  @Mock private AnswerUpdateImagePort answerUpdateImagePort;

  @InjectMocks private DiscardAnswerUpdateService service;

  @Test
  @DisplayName("discardAnswerUpdate discards latest failed update and releases pending images")
  void discardAnswerUpdateDiscardsLatestFailedUpdate() {
    DiscardAnswerUpdateCommand command = new DiscardAnswerUpdateCommand(20L, 10L, 100L);
    given(loadAnswerPort.loadAnswerForUpdate(100L)).willReturn(Optional.of(answer()));
    given(answerUpdateStatePort.discardLatestFailed(100L)).willReturn(1);

    var result = service.discardAnswerUpdate(command);

    assertThat(result.postId()).isEqualTo(10L);
    assertThat(result.answerId()).isEqualTo(100L);
    assertThat(result.pendingUpdateStatus()).isEqualTo(AnswerUpdateStatus.DISCARDED);
    verify(answerUpdateImagePort).releasePendingImages(100L);
  }

  @Test
  @DisplayName("discardAnswerUpdate fails when there is no failed update to discard")
  void discardAnswerUpdateFailsWhenNoFailedUpdateExists() {
    DiscardAnswerUpdateCommand command = new DiscardAnswerUpdateCommand(20L, 10L, 100L);
    given(loadAnswerPort.loadAnswerForUpdate(100L)).willReturn(Optional.of(answer()));
    given(answerUpdateStatePort.discardLatestFailed(100L)).willReturn(0);

    assertThatThrownBy(() -> service.discardAnswerUpdate(command))
        .isInstanceOf(AnswerPublicationStateException.class);
    verify(answerUpdateImagePort, never()).releasePendingImages(100L);
  }

  private Answer answer() {
    return Answer.builder()
        .id(100L)
        .postId(10L)
        .userId(20L)
        .content("answer")
        .isAccepted(false)
        .build();
  }
}
