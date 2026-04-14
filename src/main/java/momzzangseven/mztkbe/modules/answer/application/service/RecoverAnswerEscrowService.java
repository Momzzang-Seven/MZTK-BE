package momzzangseven.mztkbe.modules.answer.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerMutationResult;
import momzzangseven.mztkbe.modules.answer.application.dto.RecoverAnswerEscrowCommand;
import momzzangseven.mztkbe.modules.answer.application.port.in.RecoverAnswerEscrowUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.CountAnswersPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadPostPort;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recreates an answer-submit intent for a local answer that is still missing projection. */
@Service
@RequiredArgsConstructor
@Transactional
public class RecoverAnswerEscrowService implements RecoverAnswerEscrowUseCase {

  private final LoadAnswerPort loadAnswerPort;
  private final LoadPostPort loadPostPort;
  private final CountAnswersPort countAnswersPort;
  private final AnswerLifecycleExecutionPort answerLifecycleExecutionPort;

  @Override
  public AnswerMutationResult recoverAnswerCreate(RecoverAnswerEscrowCommand command) {
    command.validate();

    Answer answer =
        loadAnswerPort
            .loadAnswerForUpdate(command.answerId())
            .orElseThrow(momzzangseven.mztkbe.global.error.answer.AnswerNotFoundException::new);
    if (!answer.getPostId().equals(command.postId())) {
      throw new momzzangseven.mztkbe.global.error.answer.AnswerPostMismatchException();
    }
    answer.validateOwnership(command.requesterId());
    LoadPostPort.PostContext post =
        loadPostPort
            .loadPost(command.postId())
            .orElseThrow(momzzangseven.mztkbe.global.error.answer.AnswerPostNotFoundException::new);
    int activeAnswerCount = Math.toIntExact(countAnswersPort.countAnswers(command.postId()));

    return new AnswerMutationResult(
        command.postId(),
        command.answerId(),
        answerLifecycleExecutionPort
            .recoverAnswerCreate(
                command.postId(),
                command.answerId(),
                command.requesterId(),
                post.writerId(),
                post.content(),
                post.reward(),
                answer.getContent(),
                activeAnswerCount)
            .orElse(null));
  }
}
