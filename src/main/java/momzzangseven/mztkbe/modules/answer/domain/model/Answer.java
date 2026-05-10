package momzzangseven.mztkbe.modules.answer.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.global.error.answer.AnswerInvalidInputException;
import momzzangseven.mztkbe.global.error.answer.AnswerUnauthorizedException;
import momzzangseven.mztkbe.global.error.answer.CannotAnswerOwnPostException;
import momzzangseven.mztkbe.global.error.answer.CannotAnswerSolvedPostException;
import momzzangseven.mztkbe.global.error.answer.CannotDeleteAnswerOnSolvedPostException;
import momzzangseven.mztkbe.global.error.answer.CannotUpdateAnswerOnSolvedPostException;
import momzzangseven.mztkbe.modules.answer.domain.vo.AnswerDeleteStatus;
import momzzangseven.mztkbe.modules.answer.domain.vo.AnswerPublicationStatus;

@Getter
public class Answer {

  private final Long id;
  private final Long postId;
  private final Long userId;
  private final String content;
  private final Boolean isAccepted;
  private final AnswerPublicationStatus publicationStatus;
  private final String currentCreateExecutionIntentId;
  private final String createPreparationToken;
  private final String publicationFailureTerminalStatus;
  private final String publicationFailureReason;
  private final LocalDateTime createPreparationExpiresAt;
  private final AnswerDeleteStatus pendingDeleteStatus;
  private final String currentDeleteExecutionIntentId;
  private final String deletePreparationToken;
  private final LocalDateTime deletePreparationExpiresAt;
  private final String deleteFailureTerminalStatus;
  private final String deleteFailureReason;
  private final String reconciliationRequiredReason;
  private final String reconciliationRequiredIntentId;
  private final LocalDateTime createdAt;
  private final LocalDateTime updatedAt;

  @Builder(toBuilder = true)
  private Answer(
      Long id,
      Long postId,
      Long userId,
      String content,
      Boolean isAccepted,
      AnswerPublicationStatus publicationStatus,
      String currentCreateExecutionIntentId,
      String createPreparationToken,
      String publicationFailureTerminalStatus,
      String publicationFailureReason,
      LocalDateTime createPreparationExpiresAt,
      AnswerDeleteStatus pendingDeleteStatus,
      String currentDeleteExecutionIntentId,
      String deletePreparationToken,
      LocalDateTime deletePreparationExpiresAt,
      String deleteFailureTerminalStatus,
      String deleteFailureReason,
      String reconciliationRequiredReason,
      String reconciliationRequiredIntentId,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {
    this.id = id;
    this.postId = postId;
    this.userId = userId;
    this.content = content;
    this.isAccepted = isAccepted != null ? isAccepted : false;
    this.publicationStatus =
        publicationStatus == null ? AnswerPublicationStatus.VISIBLE : publicationStatus;
    this.currentCreateExecutionIntentId = normalizeBlank(currentCreateExecutionIntentId);
    this.createPreparationToken = normalizeBlank(createPreparationToken);
    this.publicationFailureTerminalStatus = normalizeBlank(publicationFailureTerminalStatus);
    this.publicationFailureReason = normalizeBlank(publicationFailureReason);
    this.createPreparationExpiresAt = createPreparationExpiresAt;
    this.pendingDeleteStatus = pendingDeleteStatus;
    this.currentDeleteExecutionIntentId = normalizeBlank(currentDeleteExecutionIntentId);
    this.deletePreparationToken = normalizeBlank(deletePreparationToken);
    this.deletePreparationExpiresAt = deletePreparationExpiresAt;
    this.deleteFailureTerminalStatus = normalizeBlank(deleteFailureTerminalStatus);
    this.deleteFailureReason = normalizeBlank(deleteFailureReason);
    this.reconciliationRequiredReason = normalizeBlank(reconciliationRequiredReason);
    this.reconciliationRequiredIntentId = normalizeBlank(reconciliationRequiredIntentId);
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public static Answer create(
      Long postId, Long postWriterId, boolean isPostSolved, Long answererId, String content) {

    Objects.requireNonNull(postWriterId, "postWriterId must not be null");
    Objects.requireNonNull(answererId, "answererId must not be null");

    if (isPostSolved) {
      throw new CannotAnswerSolvedPostException();
    }
    if (postWriterId.equals(answererId)) {
      throw new CannotAnswerOwnPostException();
    }
    if (content == null || content.isBlank()) {
      throw new AnswerInvalidInputException("Answer content must not be blank.");
    }

    return Answer.builder()
        .postId(postId)
        .userId(answererId)
        .content(content)
        .isAccepted(false)
        .publicationStatus(AnswerPublicationStatus.VISIBLE)
        .build();
  }

  public boolean isPubliclyVisible() {
    return publicationStatus.isPubliclyVisible()
        && pendingDeleteStatus == null
        && reconciliationRequiredReason == null;
  }

  public boolean isOwnerVisiblePendingState() {
    return createPreparationToken == null && publicationStatus.isOwnerVisible();
  }

  public boolean hasPendingDelete() {
    return pendingDeleteStatus != null;
  }

  public boolean matchesCurrentCreateExecutionIntent(String executionIntentId) {
    return executionIntentId != null
        && !executionIntentId.isBlank()
        && executionIntentId.equals(currentCreateExecutionIntentId);
  }

  public boolean matchesCurrentDeleteExecutionIntent(String executionIntentId) {
    return executionIntentId != null
        && !executionIntentId.isBlank()
        && executionIntentId.equals(currentDeleteExecutionIntentId);
  }

  public Answer reserveCreate(String preparationToken, LocalDateTime expiresAt) {
    String normalizedToken = requireToken(preparationToken, "preparationToken");
    return this.toBuilder()
        .publicationStatus(AnswerPublicationStatus.PENDING)
        .currentCreateExecutionIntentId(null)
        .createPreparationToken(normalizedToken)
        .createPreparationExpiresAt(Objects.requireNonNull(expiresAt, "expiresAt must not be null"))
        .publicationFailureTerminalStatus(null)
        .publicationFailureReason(null)
        .reconciliationRequiredReason(null)
        .reconciliationRequiredIntentId(null)
        .updatedAt(LocalDateTime.now())
        .build();
  }

  public Answer bindCreateIntent(String executionIntentId, String preparationToken) {
    String normalizedIntentId = requireToken(executionIntentId, "executionIntentId");
    String normalizedPreparationToken = requireToken(preparationToken, "preparationToken");
    if (!normalizedPreparationToken.equals(createPreparationToken)) {
      throw new AnswerInvalidInputException("Answer create preparation token mismatch.");
    }
    return this.toBuilder()
        .publicationStatus(AnswerPublicationStatus.PENDING)
        .currentCreateExecutionIntentId(normalizedIntentId)
        .createPreparationToken(null)
        .createPreparationExpiresAt(null)
        .publicationFailureTerminalStatus(null)
        .publicationFailureReason(null)
        .updatedAt(LocalDateTime.now())
        .build();
  }

  public Answer markPublicationVisible(String executionIntentId) {
    if (!matchesCurrentCreateExecutionIntent(executionIntentId)) {
      return this;
    }
    return this.toBuilder()
        .publicationStatus(AnswerPublicationStatus.VISIBLE)
        .currentCreateExecutionIntentId(null)
        .createPreparationToken(null)
        .createPreparationExpiresAt(null)
        .publicationFailureTerminalStatus(null)
        .publicationFailureReason(null)
        .reconciliationRequiredReason(null)
        .reconciliationRequiredIntentId(null)
        .updatedAt(LocalDateTime.now())
        .build();
  }

  public Answer markPublicationFailed(
      String executionIntentId, String terminalStatus, String failureReason) {
    if (!matchesCurrentCreateExecutionIntent(executionIntentId)) {
      return this;
    }
    return this.toBuilder()
        .publicationStatus(AnswerPublicationStatus.FAILED)
        .currentCreateExecutionIntentId(null)
        .createPreparationToken(null)
        .createPreparationExpiresAt(null)
        .publicationFailureTerminalStatus(normalizeBlank(terminalStatus))
        .publicationFailureReason(normalizeBlank(failureReason))
        .updatedAt(LocalDateTime.now())
        .build();
  }

  public Answer beginDelete(String preparationToken, LocalDateTime expiresAt) {
    if (!isPubliclyVisible()) {
      throw new AnswerInvalidInputException("Only visible answers can begin delete lifecycle.");
    }
    return this.toBuilder()
        .pendingDeleteStatus(AnswerDeleteStatus.PREPARING)
        .currentDeleteExecutionIntentId(null)
        .deletePreparationToken(requireToken(preparationToken, "preparationToken"))
        .deletePreparationExpiresAt(Objects.requireNonNull(expiresAt, "expiresAt must not be null"))
        .deleteFailureTerminalStatus(null)
        .deleteFailureReason(null)
        .updatedAt(LocalDateTime.now())
        .build();
  }

  public Answer bindDeleteIntent(String executionIntentId, String preparationToken) {
    String normalizedIntentId = requireToken(executionIntentId, "executionIntentId");
    String normalizedPreparationToken = requireToken(preparationToken, "preparationToken");
    if (!normalizedPreparationToken.equals(deletePreparationToken)) {
      throw new AnswerInvalidInputException("Answer delete preparation token mismatch.");
    }
    return this.toBuilder()
        .pendingDeleteStatus(AnswerDeleteStatus.PENDING)
        .currentDeleteExecutionIntentId(normalizedIntentId)
        .deletePreparationToken(null)
        .deletePreparationExpiresAt(null)
        .deleteFailureTerminalStatus(null)
        .deleteFailureReason(null)
        .updatedAt(LocalDateTime.now())
        .build();
  }

  public Answer rollbackDelete(
      String executionIntentId, String terminalStatus, String failureReason) {
    if (!matchesCurrentDeleteExecutionIntent(executionIntentId)) {
      return this;
    }
    return this.toBuilder()
        .pendingDeleteStatus(null)
        .currentDeleteExecutionIntentId(null)
        .deletePreparationToken(null)
        .deletePreparationExpiresAt(null)
        .deleteFailureTerminalStatus(normalizeBlank(terminalStatus))
        .deleteFailureReason(normalizeBlank(failureReason))
        .updatedAt(LocalDateTime.now())
        .build();
  }

  public Answer clearDeleteFailure() {
    return this.toBuilder()
        .pendingDeleteStatus(null)
        .currentDeleteExecutionIntentId(null)
        .deletePreparationToken(null)
        .deletePreparationExpiresAt(null)
        .deleteFailureTerminalStatus(null)
        .deleteFailureReason(null)
        .updatedAt(LocalDateTime.now())
        .build();
  }

  public Answer markReconciliationRequired(String reason, String executionIntentId) {
    return this.toBuilder()
        .publicationStatus(AnswerPublicationStatus.RECONCILIATION_REQUIRED)
        .reconciliationRequiredReason(normalizeBlank(reason))
        .reconciliationRequiredIntentId(normalizeBlank(executionIntentId))
        .updatedAt(LocalDateTime.now())
        .build();
  }

  public void validateOwnership(Long currentUserId) {
    Objects.requireNonNull(currentUserId, "currentUserId must not be null");
    if (!this.userId.equals(currentUserId)) {
      throw new AnswerUnauthorizedException();
    }
  }

  public void validateDeletable(Long requesterId, boolean parentQuestionSolved) {
    validateOwnership(requesterId);
    if (parentQuestionSolved) {
      throw new CannotDeleteAnswerOnSolvedPostException();
    }
  }

  public Answer update(String content, Long requesterId, boolean parentQuestionSolved) {
    validateOwnership(requesterId);
    if (parentQuestionSolved) {
      throw new CannotUpdateAnswerOnSolvedPostException();
    }

    var builder = this.toBuilder();
    boolean isUpdated = false;

    if (content != null) {
      if (content.isBlank()) {
        throw new AnswerInvalidInputException("Updated content must not be blank.");
      }
      builder.content(content);
      isUpdated = true;
    }

    return isUpdated ? builder.build() : this;
  }

  public Answer accept() {
    if (this.isAccepted) {
      return this;
    }
    return this.toBuilder().isAccepted(true).updatedAt(LocalDateTime.now()).build();
  }

  public Answer confirmContentUpdate(String confirmedContent) {
    if (confirmedContent == null || confirmedContent.isBlank()) {
      throw new AnswerInvalidInputException("Confirmed content must not be blank.");
    }
    if (Objects.equals(this.content, confirmedContent)) {
      return this;
    }
    return this.toBuilder().content(confirmedContent).updatedAt(LocalDateTime.now()).build();
  }

  private static String normalizeBlank(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static String requireToken(String value, String fieldName) {
    String normalized = normalizeBlank(value);
    if (normalized == null) {
      throw new AnswerInvalidInputException(fieldName + " must not be blank.");
    }
    return normalized;
  }
}
