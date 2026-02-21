package momzzangseven.mztkbe.modules.web3.transfer.application.resolver;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.QuestionRewardIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntent;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.ResolvedReward;
import org.springframework.stereotype.Component;

/** Resolves QUESTION_REWARD from intent SSOT registered by acceptance domain. */
@Component
@RequiredArgsConstructor
public class QuestionRewardResolver implements DomainRewardResolver {

  private final QuestionRewardIntentPersistencePort questionRewardIntentPersistencePort;

  @Override
  public boolean supports(DomainReferenceType type) {
    return type == DomainReferenceType.QUESTION_REWARD;
  }

  @Override
  public ResolvedReward resolve(Long requesterId, String referenceId) {
    Long postId = parsePostId(referenceId);
    QuestionRewardIntent intent =
        questionRewardIntentPersistencePort
            .findByPostId(postId)
            .orElseThrow(
                () ->
                    new Web3InvalidInputException(
                        "question reward intent not found for post: " + postId));

    intent.assertResolvableBy(requesterId);

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
