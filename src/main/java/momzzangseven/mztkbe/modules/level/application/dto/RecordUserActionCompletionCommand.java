package momzzangseven.mztkbe.modules.level.application.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.level.domain.model.ActionType;

/**
 * Command for recording a user action completion (certification).
 *
 * <p>This is independent from XP rewards; reward logic is handled separately.
 */
public record RecordUserActionCompletionCommand(
    Long userId,
    ActionType actionType,
    LocalDateTime occurredAt,
    String idempotencyKey,
    String sourceRef) {

  public static RecordUserActionCompletionCommand of(
      Long userId,
      ActionType actionType,
      LocalDateTime occurredAt,
      String idempotencyKey,
      String sourceRef) {
    return new RecordUserActionCompletionCommand(
        userId, actionType, occurredAt, idempotencyKey, sourceRef);
  }

  public void validate() {
    if (userId == null) {
      throw new IllegalArgumentException("userId is required");
    }
    if (actionType == null) {
      throw new IllegalArgumentException("actionType is required");
    }
    if (occurredAt == null) {
      throw new IllegalArgumentException("occurredAt is required");
    }
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("idempotencyKey is required");
    }
  }
}
