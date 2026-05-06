package momzzangseven.mztkbe.modules.web3.qna.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaQuestionUpdateStateStatus;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class QnaQuestionUpdateState {

  private final Long id;
  private final Long postId;
  private final Long requesterUserId;
  private final Long updateVersion;
  private final String updateToken;
  private final String expectedQuestionHash;
  private final String executionIntentPublicId;
  private final QnaQuestionUpdateStateStatus status;
  @Builder.Default private final boolean preparationRetryable = true;
  private final String lastErrorCode;
  private final String lastErrorReason;
  private final LocalDateTime createdAt;
  private final LocalDateTime updatedAt;

  public static QnaQuestionUpdateState preparing(
      Long postId,
      Long requesterUserId,
      Long updateVersion,
      String updateToken,
      String expectedQuestionHash,
      LocalDateTime now) {
    validatePositive(postId, "postId");
    validatePositive(requesterUserId, "requesterUserId");
    validatePositive(updateVersion, "updateVersion");
    validateRequired(updateToken, "updateToken");
    validateRequired(expectedQuestionHash, "expectedQuestionHash");
    if (now == null) {
      throw new Web3InvalidInputException("now is required");
    }
    return QnaQuestionUpdateState.builder()
        .postId(postId)
        .requesterUserId(requesterUserId)
        .updateVersion(updateVersion)
        .updateToken(updateToken)
        .expectedQuestionHash(expectedQuestionHash)
        .status(QnaQuestionUpdateStateStatus.PREPARING)
        .preparationRetryable(true)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  public boolean matches(Long version, String token) {
    return updateVersion != null
        && updateVersion.equals(version)
        && updateToken != null
        && updateToken.equals(token);
  }

  public boolean matchesIntent(String publicId) {
    return executionIntentPublicId != null && executionIntentPublicId.equals(publicId);
  }

  public boolean matchesExpectedHash(String questionHash) {
    return expectedQuestionHash != null && expectedQuestionHash.equals(questionHash);
  }

  public QnaQuestionUpdateState bindIntent(String intentPublicId, LocalDateTime now) {
    validateRequired(intentPublicId, "intentPublicId");
    return toBuilder()
        .executionIntentPublicId(intentPublicId)
        .status(QnaQuestionUpdateStateStatus.INTENT_BOUND)
        .preparationRetryable(true)
        .lastErrorCode(null)
        .lastErrorReason(null)
        .updatedAt(now)
        .build();
  }

  public QnaQuestionUpdateState markPreparationFailed(
      String errorCode, String errorReason, boolean retryable, LocalDateTime now) {
    return toBuilder()
        .status(QnaQuestionUpdateStateStatus.PREPARATION_FAILED)
        .preparationRetryable(retryable)
        .lastErrorCode(errorCode)
        .lastErrorReason(errorReason)
        .updatedAt(now)
        .build();
  }

  public boolean isRetryablePreparationFailure() {
    return status == QnaQuestionUpdateStateStatus.PREPARATION_FAILED && preparationRetryable;
  }

  public QnaQuestionUpdateState markConfirmed(LocalDateTime now) {
    return toBuilder()
        .status(QnaQuestionUpdateStateStatus.CONFIRMED)
        .lastErrorCode(null)
        .lastErrorReason(null)
        .updatedAt(now)
        .build();
  }

  public QnaQuestionUpdateState markStale(LocalDateTime now) {
    return toBuilder().status(QnaQuestionUpdateStateStatus.STALE).updatedAt(now).build();
  }

  private static void validatePositive(Long value, String fieldName) {
    if (value == null || value <= 0) {
      throw new Web3InvalidInputException(fieldName + " must be positive");
    }
  }

  private static void validateRequired(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new Web3InvalidInputException(fieldName + " is required");
    }
  }
}
