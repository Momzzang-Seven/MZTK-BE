package momzzangseven.mztkbe.modules.post.infrastructure.external.web3;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrecheckQuestionCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerAcceptCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionDeleteCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionUpdateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.QuestionEscrowExecutionUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class QuestionLifecycleExecutionAdapter implements QuestionLifecycleExecutionPort {

  private final QuestionEscrowExecutionUseCase questionEscrowExecutionUseCase;

  @Override
  public void precheckQuestionCreate(Long requesterUserId, Long rewardMztk) {
    questionEscrowExecutionUseCase.precheckQuestionCreate(
        new PrecheckQuestionCreateCommand(requesterUserId, rewardMztk));
  }

  @Override
  public void prepareQuestionCreate(
      Long postId, Long requesterUserId, String questionContent, Long rewardMztk) {
    questionEscrowExecutionUseCase.prepareQuestionCreate(
        new PrepareQuestionCreateCommand(postId, requesterUserId, questionContent, rewardMztk));
  }

  @Override
  public void prepareQuestionUpdate(
      Long postId, Long requesterUserId, String questionContent, Long rewardMztk) {
    questionEscrowExecutionUseCase.prepareQuestionUpdate(
        new PrepareQuestionUpdateCommand(postId, requesterUserId, questionContent, rewardMztk));
  }

  @Override
  public void prepareQuestionDelete(
      Long postId, Long requesterUserId, String questionContent, Long rewardMztk) {
    questionEscrowExecutionUseCase.prepareQuestionDelete(
        new PrepareQuestionDeleteCommand(postId, requesterUserId, questionContent, rewardMztk));
  }

  @Override
  public void prepareAnswerAccept(
      Long postId,
      Long answerId,
      Long requesterUserId,
      Long answerWriterUserId,
      String questionContent,
      String answerContent,
      Long rewardMztk) {
    questionEscrowExecutionUseCase.prepareAnswerAccept(
        new PrepareAnswerAcceptCommand(
            postId,
            answerId,
            requesterUserId,
            answerWriterUserId,
            questionContent,
            answerContent,
            rewardMztk));
  }
}
