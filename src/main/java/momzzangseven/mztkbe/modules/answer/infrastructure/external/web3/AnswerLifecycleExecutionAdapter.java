package momzzangseven.mztkbe.modules.answer.infrastructure.external.web3;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerDeleteCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerUpdateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.AnswerEscrowExecutionUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class AnswerLifecycleExecutionAdapter implements AnswerLifecycleExecutionPort {

  private final AnswerEscrowExecutionUseCase answerEscrowExecutionUseCase;

  @Override
  public void prepareAnswerCreate(
      Long postId,
      Long answerId,
      Long requesterUserId,
      Long questionWriterUserId,
      String questionContent,
      Long rewardMztk,
      String answerContent,
      int activeAnswerCount) {
    answerEscrowExecutionUseCase.prepareAnswerCreate(
        new PrepareAnswerCreateCommand(
            postId,
            answerId,
            requesterUserId,
            questionWriterUserId,
            questionContent,
            rewardMztk,
            answerContent,
            activeAnswerCount));
  }

  @Override
  public void prepareAnswerUpdate(
      Long postId,
      Long answerId,
      Long requesterUserId,
      Long questionWriterUserId,
      String questionContent,
      Long rewardMztk,
      String answerContent,
      int activeAnswerCount) {
    answerEscrowExecutionUseCase.prepareAnswerUpdate(
        new PrepareAnswerUpdateCommand(
            postId,
            answerId,
            requesterUserId,
            questionWriterUserId,
            questionContent,
            rewardMztk,
            answerContent,
            activeAnswerCount));
  }

  @Override
  public void prepareAnswerDelete(
      Long postId,
      Long answerId,
      Long requesterUserId,
      Long questionWriterUserId,
      String questionContent,
      Long rewardMztk,
      int activeAnswerCount) {
    answerEscrowExecutionUseCase.prepareAnswerDelete(
        new PrepareAnswerDeleteCommand(
            postId,
            answerId,
            requesterUserId,
            questionWriterUserId,
            questionContent,
            rewardMztk,
            activeAnswerCount));
  }
}
