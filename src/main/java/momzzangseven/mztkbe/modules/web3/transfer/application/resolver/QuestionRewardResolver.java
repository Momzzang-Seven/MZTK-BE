package momzzangseven.mztkbe.modules.web3.transfer.application.resolver;

import java.math.BigInteger;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository.PostJpaRepository;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.config.RewardTokenProperties;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.ResolvedReward;
import org.springframework.stereotype.Component;

/** Resolves QUESTION_REWARD from posts SSOT. */
@Component
@RequiredArgsConstructor
public class QuestionRewardResolver implements DomainRewardResolver {

  private final PostJpaRepository postJpaRepository;
  private final RewardTokenProperties rewardTokenProperties;

  @Override
  public boolean supports(DomainReferenceType type) {
    return type == DomainReferenceType.QUESTION_REWARD;
  }

  @Override
  public ResolvedReward resolve(Long requesterId, String referenceId) {
    Long answerCommentId = parseAnswerCommentId(referenceId);
    PostJpaRepository.QuestionRewardSourceSnapshot source =
        postJpaRepository
            .findQuestionRewardSourceByAnswerCommentId(answerCommentId)
            .orElseThrow(
                () ->
                    new Web3InvalidInputException(
                        "question reward source not found for answer comment: " + answerCommentId));

    if (!PostType.QUESTION.name().equals(source.getPostType())) {
      throw new Web3InvalidInputException("reference post is not QUESTION type");
    }
    if (isFlagOn(source.getPostSolved())) {
      throw new Web3InvalidInputException("question reward is already settled for this post");
    }
    if (source.getPostOwnerUserId() == null || source.getPostOwnerUserId() <= 0) {
      throw new Web3InvalidInputException("question post has invalid owner userId");
    }
    if (!Objects.equals(requesterId, source.getPostOwnerUserId())) {
      throw new Web3InvalidInputException("only question owner can prepare question reward");
    }
    if (source.getAnswerWriterUserId() == null || source.getAnswerWriterUserId() <= 0) {
      throw new Web3InvalidInputException("accepted answer has invalid writer userId");
    }
    if (isFlagOn(source.getAnswerDeleted())) {
      throw new Web3InvalidInputException("accepted answer comment is deleted");
    }
    if (source.getReward() == null || source.getReward() <= 0) {
      throw new Web3InvalidInputException("question post has invalid reward amount");
    }

    BigInteger amountWei =
        BigInteger.valueOf(source.getReward())
            .multiply(BigInteger.TEN.pow(Math.max(0, rewardTokenProperties.getDecimals())));

    return new ResolvedReward(source.getAnswerWriterUserId(), amountWei);
  }

  private boolean isFlagOn(Integer flag) {
    return flag != null && flag != 0;
  }

  private Long parseAnswerCommentId(String referenceId) {
    if (referenceId == null || referenceId.isBlank()) {
      throw new Web3InvalidInputException("referenceId is required");
    }
    try {
      long parsed = Long.parseLong(referenceId);
      if (parsed <= 0) {
        throw new Web3InvalidInputException(
            "referenceId must be positive numeric answer comment id");
      }
      return parsed;
    } catch (NumberFormatException e) {
      throw new Web3InvalidInputException("referenceId must be numeric answer comment id");
    }
  }
}
