package momzzangseven.mztkbe.modules.web3.transfer.domain.model;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class QuestionRewardIntent {

  private final Long id;
  private final Long postId;
  private final Long acceptedCommentId;
  private final Long fromUserId;
  private final Long toUserId;
  private final BigInteger amountWei;
  private final QuestionRewardIntentStatus status;
  private final String lastExecutionIntentErrorCode;
  private final String lastExecutionIntentErrorReason;
  private final LocalDateTime createdAt;
  private final LocalDateTime updatedAt;

  public void assertResolvableBy(Long requesterId) {
    if (status == QuestionRewardIntentStatus.SUCCEEDED) {
      throw new Web3InvalidInputException("question reward is already settled for this post");
    }
    if (status == QuestionRewardIntentStatus.SUBMITTED) {
      throw new Web3InvalidInputException("question reward is already in submitted state");
    }
    if (status == QuestionRewardIntentStatus.CANCELED) {
      throw new Web3InvalidInputException("question reward intent is canceled");
    }
    if (status == QuestionRewardIntentStatus.FAILED_ONCHAIN) {
      throw new Web3InvalidInputException("question reward failed onchain; re-register intent");
    }
    if (fromUserId == null || fromUserId <= 0) {
      throw new Web3InvalidInputException("question reward intent has invalid fromUserId");
    }
    if (!Objects.equals(requesterId, fromUserId)) {
      throw new Web3InvalidInputException("only question owner can prepare question reward");
    }
    if (toUserId == null || toUserId <= 0) {
      throw new Web3InvalidInputException("accepted answer has invalid writer userId");
    }
    if (acceptedCommentId == null || acceptedCommentId <= 0) {
      throw new Web3InvalidInputException("question reward intent has invalid acceptedCommentId");
    }
    if (amountWei == null || amountWei.signum() <= 0) {
      throw new Web3InvalidInputException("question reward intent has invalid amountWei");
    }
  }

  public boolean isImmutableForRegister() {
    return status == QuestionRewardIntentStatus.SUBMITTED
        || status == QuestionRewardIntentStatus.SUCCEEDED;
  }

  public boolean isSamePayload(
      Long newAcceptedCommentId, Long newFromUserId, Long newToUserId, BigInteger newAmountWei) {
    return Objects.equals(acceptedCommentId, newAcceptedCommentId)
        && Objects.equals(fromUserId, newFromUserId)
        && Objects.equals(toUserId, newToUserId)
        && amountWei != null
        && amountWei.compareTo(newAmountWei) == 0;
  }

  public QuestionRewardIntent withPrepareRequired(
      Long newAcceptedCommentId, Long newFromUserId, Long newToUserId, BigInteger newAmountWei) {
    return toBuilder()
        .acceptedCommentId(newAcceptedCommentId)
        .fromUserId(newFromUserId)
        .toUserId(newToUserId)
        .amountWei(newAmountWei)
        .status(QuestionRewardIntentStatus.PREPARE_REQUIRED)
        .lastExecutionIntentErrorCode(null)
        .lastExecutionIntentErrorReason(null)
        .build();
  }

  public QuestionRewardIntent markExecutionIntentCreationFailed(
      String errorCode, String errorReason) {
    return toBuilder()
        .lastExecutionIntentErrorCode(errorCode)
        .lastExecutionIntentErrorReason(errorReason)
        .build();
  }

  public boolean isStaleCancelRequest(Long requestAcceptedCommentId) {
    if (requestAcceptedCommentId == null) {
      return false;
    }
    return !Objects.equals(acceptedCommentId, requestAcceptedCommentId);
  }

  public boolean cannotCancel() {
    return status == QuestionRewardIntentStatus.SUCCEEDED
        || status == QuestionRewardIntentStatus.CANCELED
        || status == QuestionRewardIntentStatus.SUBMITTED;
  }

  public QuestionRewardIntent cancel() {
    return toBuilder().status(QuestionRewardIntentStatus.CANCELED).build();
  }
}
