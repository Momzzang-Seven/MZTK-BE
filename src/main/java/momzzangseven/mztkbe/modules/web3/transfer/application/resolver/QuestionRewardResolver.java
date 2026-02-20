package momzzangseven.mztkbe.modules.web3.transfer.application.resolver;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.ResolvedReward;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity.QuestionRewardIntentEntity;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.repository.QuestionRewardIntentJpaRepository;
import org.springframework.stereotype.Component;

/** Resolves QUESTION_REWARD from intent SSOT registered by acceptance domain. */
@Component
@RequiredArgsConstructor
public class QuestionRewardResolver implements DomainRewardResolver {

  private final QuestionRewardIntentJpaRepository questionRewardIntentJpaRepository;

  @Override
  public boolean supports(DomainReferenceType type) {
    return type == DomainReferenceType.QUESTION_REWARD;
  }

  @Override
  public ResolvedReward resolve(Long requesterId, String referenceId) {
    Long postId = parsePostId(referenceId);
    QuestionRewardIntentEntity intent =
        questionRewardIntentJpaRepository
            .findByPostId(postId)
            .orElseThrow(
                () ->
                    new Web3InvalidInputException(
                        "question reward intent not found for post: " + postId));

    if (intent.getStatus() == QuestionRewardIntentStatus.SUCCEEDED) {
      throw new Web3InvalidInputException("question reward is already settled for this post");
    }
    if (intent.getStatus() == QuestionRewardIntentStatus.SUBMITTED) {
      throw new Web3InvalidInputException("question reward is already in submitted state");
    }
    if (intent.getStatus() == QuestionRewardIntentStatus.CANCELED) {
      throw new Web3InvalidInputException("question reward intent is canceled");
    }
    if (intent.getStatus() == QuestionRewardIntentStatus.FAILED_ONCHAIN) {
      throw new Web3InvalidInputException("question reward failed onchain; re-register intent");
    }
    if (intent.getFromUserId() == null || intent.getFromUserId() <= 0) {
      throw new Web3InvalidInputException("question reward intent has invalid fromUserId");
    }
    if (!Objects.equals(requesterId, intent.getFromUserId())) {
      throw new Web3InvalidInputException("only question owner can prepare question reward");
    }
    if (intent.getToUserId() == null || intent.getToUserId() <= 0) {
      throw new Web3InvalidInputException("accepted answer has invalid writer userId");
    }
    if (intent.getAcceptedCommentId() == null || intent.getAcceptedCommentId() <= 0) {
      throw new Web3InvalidInputException("question reward intent has invalid acceptedCommentId");
    }
    if (intent.getAmountWei() == null || intent.getAmountWei().signum() <= 0) {
      throw new Web3InvalidInputException("question reward intent has invalid amountWei");
    }

    return new ResolvedReward(
        intent.getToUserId(), intent.getAmountWei(), intent.getAcceptedCommentId());
  }

  private Long parsePostId(String referenceId) {
    if (referenceId == null || referenceId.isBlank()) {
      throw new Web3InvalidInputException("referenceId is required");
    }
    try {
      long parsed = Long.parseLong(referenceId);
      if (parsed <= 0) {
        throw new Web3InvalidInputException("referenceId must be positive numeric post id");
      }
      return parsed;
    } catch (NumberFormatException e) {
      throw new Web3InvalidInputException("referenceId must be numeric post id");
    }
  }
}
