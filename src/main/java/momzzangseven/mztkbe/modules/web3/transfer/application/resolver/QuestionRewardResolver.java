package momzzangseven.mztkbe.modules.web3.transfer.application.resolver;

import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostEntity;
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
    Long postId = parsePostId(referenceId);
    PostEntity post =
        postJpaRepository
            .findById(postId)
            .orElseThrow(() -> new Web3InvalidInputException("question post not found: " + postId));

    if (post.getType() != PostType.QUESTION) {
      throw new Web3InvalidInputException("reference post is not QUESTION type");
    }
    if (Boolean.TRUE.equals(post.getIsSolved())) {
      throw new Web3InvalidInputException("question reward is already settled for this post");
    }
    if (post.getUserId() == null || post.getUserId() <= 0) {
      throw new Web3InvalidInputException("question post has invalid owner userId");
    }
    if (post.getReward() == null || post.getReward() <= 0) {
      throw new Web3InvalidInputException("question post has invalid reward amount");
    }

    BigInteger amountWei =
        BigInteger.valueOf(post.getReward())
            .multiply(BigInteger.TEN.pow(Math.max(0, rewardTokenProperties.getDecimals())));

    return new ResolvedReward(post.getUserId(), amountWei);
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
