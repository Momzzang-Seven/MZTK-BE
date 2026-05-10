package momzzangseven.mztkbe.modules.answer.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.answer.AnswerNotFoundException;
import momzzangseven.mztkbe.global.error.answer.AnswerPostMismatchException;
import momzzangseven.mztkbe.global.error.answer.AnswerPublicationStateException;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerMutationResult;
import momzzangseven.mztkbe.modules.answer.application.dto.DiscardAnswerUpdateCommand;
import momzzangseven.mztkbe.modules.answer.application.port.in.DiscardAnswerUpdateUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerUpdateImagePort;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerUpdateStatePort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;
import momzzangseven.mztkbe.modules.answer.domain.vo.AnswerUpdateStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DiscardAnswerUpdateService implements DiscardAnswerUpdateUseCase {

  private final LoadAnswerPort loadAnswerPort;
  private final AnswerUpdateStatePort answerUpdateStatePort;
  private final AnswerUpdateImagePort answerUpdateImagePort;

  @Override
  @Transactional
  public AnswerMutationResult discardAnswerUpdate(DiscardAnswerUpdateCommand command) {
    command.validate();
    Answer answer =
        loadAnswerPort
            .loadAnswerForUpdate(command.answerId())
            .orElseThrow(AnswerNotFoundException::new);
    if (!answer.getPostId().equals(command.postId())) {
      throw new AnswerPostMismatchException();
    }
    answer.validateOwnership(command.requesterId());
    int discarded = answerUpdateStatePort.discardLatestFailed(command.answerId());
    if (discarded == 0) {
      throw new AnswerPublicationStateException(ErrorCode.ANSWER_UPDATE_DISCARD_UNAVAILABLE);
    }
    answerUpdateImagePort.releasePendingImages(command.answerId());
    return new AnswerMutationResult(
        answer.getPostId(),
        answer.getId(),
        answer.getPublicationStatus(),
        AnswerUpdateStatus.DISCARDED,
        null,
        null);
  }
}
