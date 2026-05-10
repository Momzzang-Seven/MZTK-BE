package momzzangseven.mztkbe.modules.answer.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.dto.SyncAnswerPublicationStateCommand;
import momzzangseven.mztkbe.modules.answer.application.port.in.ConfirmAnswerSubmittedUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.FailAnswerSubmitUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.out.SaveAnswerPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SyncAnswerPublicationStateService
    implements ConfirmAnswerSubmittedUseCase, FailAnswerSubmitUseCase {

  private final SaveAnswerPort saveAnswerPort;

  @Override
  @Transactional
  public void confirmAnswerSubmitted(SyncAnswerPublicationStateCommand command) {
    command.validateForConfirm();
    saveAnswerPort.confirmCreateIfCurrent(command.answerId(), command.executionIntentId());
  }

  @Override
  @Transactional
  public void failAnswerSubmit(SyncAnswerPublicationStateCommand command) {
    command.validateForFailure();
    saveAnswerPort.markCreateFailedIfCurrent(
        command.answerId(),
        command.executionIntentId(),
        command.terminalStatus(),
        command.failureReason());
  }
}
