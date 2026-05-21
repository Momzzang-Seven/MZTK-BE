package momzzangseven.mztkbe.modules.web3.marketplace.domain.vo;

import java.util.Locale;
import java.util.Set;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/** Builds deterministic root idempotency keys for marketplace admin execution intents. */
public final class MarketplaceAdminEscrowIdempotencyKeyFactory {

  private static final Set<String> REFUND_REASONS =
      Set.of("TRAINER_TIMEOUT", "SESSION_START_WINDOW_TIMEOUT", "ADMIN_MANUAL_REFUND");
  private static final Set<String> SETTLE_REASONS =
      Set.of("BUYER_CONFIRMATION_TIMEOUT", "ADMIN_MANUAL_SETTLE");

  private MarketplaceAdminEscrowIdempotencyKeyFactory() {}

  public static String create(
      MarketplaceExecutionActionType actionType,
      Long reservationId,
      MarketplaceAdminExecutionRequestSource requestSource,
      String reasonCode) {
    if (actionType == null || !actionType.isAdminAction()) {
      throw new Web3InvalidInputException("admin actionType is required");
    }
    if (reservationId == null || reservationId <= 0) {
      throw new Web3InvalidInputException("reservationId must be positive");
    }
    if (requestSource == null) {
      throw new Web3InvalidInputException("requestSource is required");
    }
    String normalizedReason = normalizeReason(actionType, reasonCode);
    return "marketplace-admin:"
        + actionType.name().toLowerCase(Locale.ROOT)
        + ":"
        + reservationId
        + ":"
        + requestSource.name().toLowerCase(Locale.ROOT)
        + ":"
        + normalizedReason.toLowerCase(Locale.ROOT);
  }

  private static String normalizeReason(
      MarketplaceExecutionActionType actionType, String reasonCode) {
    if (reasonCode == null || reasonCode.isBlank()) {
      throw new Web3InvalidInputException("reasonCode is required");
    }
    String normalized = reasonCode.trim().toUpperCase(Locale.ROOT);
    boolean allowed =
        switch (actionType) {
          case MARKETPLACE_ADMIN_REFUND -> REFUND_REASONS.contains(normalized);
          case MARKETPLACE_ADMIN_SETTLE -> SETTLE_REASONS.contains(normalized);
          case MARKETPLACE_CLASS_PURCHASE,
                  MARKETPLACE_CLASS_CANCEL,
                  MARKETPLACE_CLASS_CONFIRM,
                  MARKETPLACE_CLASS_EXPIRED_REFUND ->
              throw new Web3InvalidInputException("admin actionType is required");
        };
    if (!allowed) {
      throw new Web3InvalidInputException("reasonCode is not valid for actionType");
    }
    return normalized;
  }
}
