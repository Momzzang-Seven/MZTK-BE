package momzzangseven.mztkbe.modules.answer.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.dto.SyncAnswerUpdateStateCommand;
import momzzangseven.mztkbe.modules.answer.application.port.in.ConfirmAnswerUpdateUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.FailAnswerUpdateUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerUpdateImagePort;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerUpdateStatePort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.SaveAnswerPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SyncAnswerUpdateStateService
    implements ConfirmAnswerUpdateUseCase, FailAnswerUpdateUseCase {

  private final AnswerUpdateStatePort answerUpdateStatePort;
  private final AnswerUpdateImagePort answerUpdateImagePort;
  private final LoadAnswerPort loadAnswerPort;
  private final SaveAnswerPort saveAnswerPort;

  @Override
  @Transactional
  public void confirmAnswerUpdate(SyncAnswerUpdateStateCommand command) {
    command.validateForConfirm();
    answerUpdateStatePort
        .loadIntentBoundState(
            command.answerId(),
            command.updateVersion(),
            command.updateToken(),
            command.executionIntentId())
        .ifPresent(
            state ->
                loadAnswerPort
                    .loadAnswerForUpdate(command.answerId())
                    .ifPresent(
                        answer -> {
                          saveAnswerPort.saveAnswer(
                              answer.confirmContentUpdate(state.pendingContent()));
                          answerUpdateImagePort.applyPendingImages(
                              state.id(), answer.getUserId(), answer.getId());
                          answerUpdateStatePort.markConfirmed(state.id());
                        }));
  }

  @Override
  @Transactional
  public void failAnswerUpdate(SyncAnswerUpdateStateCommand command) {
    command.validateForConfirm();
    answerUpdateStatePort.markFailedIfCurrent(
        command.answerId(),
        command.updateVersion(),
        command.updateToken(),
        command.executionIntentId(),
        command.failureReason());
  }
}
