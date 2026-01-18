package momzzangseven.mztkbe.modules.level.application.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.level.domain.model.XpType;

/**
 * Command for granting XP.
 *
 * <p>Used as an internal hook from other modules after an action is successfully completed.
 */
public record GrantXpCommand(
    Long userId, XpType xpType, LocalDateTime occurredAt, String idempotencyKey, String sourceRef) {

  public static GrantXpCommand of(
      Long userId,
      XpType xpType,
      LocalDateTime occurredAt,
      String idempotencyKey,
      String sourceRef) {
    return new GrantXpCommand(userId, xpType, occurredAt, idempotencyKey, sourceRef);
  }

  public void validate() {
    if (userId == null) {
      throw new IllegalArgumentException("userId is required");
    }
    if (xpType == null) {
      throw new IllegalArgumentException("xpType is required");
    }
    if (occurredAt == null) {
      throw new IllegalArgumentException("occurredAt is required");
    }
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("idempotencyKey is required");
    }
  }
}
