package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.post.application.port.out.RequestQuestionRewardOnAcceptPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.RegisterQuestionRewardIntentCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.PublishQuestionRewardIntentEventUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.config.TransferRewardTokenProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class QuestionRewardRequestAdapter implements RequestQuestionRewardOnAcceptPort {

  private final PublishQuestionRewardIntentEventUseCase publishQuestionRewardIntentEventUseCase;
  private final TransferRewardTokenProperties rewardTokenProperties;

  @Override
  public void request(
      Long postId,
      Long acceptedAnswerId,
      Long requesterUserId,
      Long answerWriterUserId,
      Long rewardMztk) {
    validate(postId, acceptedAnswerId, requesterUserId, answerWriterUserId, rewardMztk);

    publishQuestionRewardIntentEventUseCase.publishRegisterRequested(
        new RegisterQuestionRewardIntentCommand(
            postId, acceptedAnswerId, requesterUserId, answerWriterUserId, toWei(rewardMztk)));
  }

  private void validate(
      Long postId,
      Long acceptedAnswerId,
      Long requesterUserId,
      Long answerWriterUserId,
      Long rewardMztk) {
    if (postId == null || postId <= 0) {
      throw new Web3InvalidInputException("postId must be positive");
    }
    if (acceptedAnswerId == null || acceptedAnswerId <= 0) {
      throw new Web3InvalidInputException("acceptedAnswerId must be positive");
    }
    if (requesterUserId == null || requesterUserId <= 0) {
      throw new Web3InvalidInputException("requesterUserId must be positive");
    }
    if (answerWriterUserId == null || answerWriterUserId <= 0) {
      throw new Web3InvalidInputException("answerWriterUserId must be positive");
    }
    if (rewardMztk == null || rewardMztk <= 0) {
      throw new Web3InvalidInputException("rewardMztk must be positive");
    }
  }

  private BigInteger toWei(Long rewardMztk) {
    return BigInteger.valueOf(rewardMztk)
        .multiply(BigInteger.TEN.pow(Math.max(0, rewardTokenProperties.getDecimals())));
  }
}
