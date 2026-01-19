package momzzangseven.mztkbe.modules.level.application.dto;

import java.time.LocalDateTime;
import java.util.regex.Pattern;
import momzzangseven.mztkbe.modules.level.domain.model.XpType;

/**
 * Command for granting XP.
 *
 * <p>Used as an internal hook from other modules after an action is successfully completed.
 */
public record GrantXpCommand(
    Long userId, XpType xpType, LocalDateTime occurredAt, String idempotencyKey, String sourceRef) {

  private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 200;
  private static final Pattern IDEMPOTENCY_KEY_PATTERN =
      Pattern.compile("^[A-Za-z0-9][A-Za-z0-9:_\\-.]{0,199}$");

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
    validateIdempotencyKey(xpType, idempotencyKey);
  }

  private static void validateIdempotencyKey(XpType type, String key) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("idempotencyKey is required");
    }
    if (key.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
      throw new IllegalArgumentException(
          "idempotencyKey must be <= " + IDEMPOTENCY_KEY_MAX_LENGTH + " characters");
    }
    if (!IDEMPOTENCY_KEY_PATTERN.matcher(key).matches()) {
      throw new IllegalArgumentException(
          "idempotencyKey contains invalid characters (allowed: A-Z a-z 0-9 : _ - .)");
    }

    String prefix = recommendedPrefix(type);
    if (prefix != null && !key.startsWith(prefix)) {
      throw new IllegalArgumentException("idempotencyKey must start with prefix: " + prefix);
    }
  }

  private static String recommendedPrefix(XpType type) {
    return switch (type) {
      case CHECK_IN -> "checkin:";
      case STREAK_7D -> "streak7:";
      case WORKOUT -> "workout:";
      case POST -> "post:";
      case COMMENT -> "comment:";
    };
  }
}
