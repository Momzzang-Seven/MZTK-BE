package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.util.EnumSet;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminReviewValidationCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationActionState;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.MarketplaceAdminAttemptFailureStage;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationActionStateStatus;

/** Single source for deriving admin attempt failure stage from action-state status/error code. */
public final class MarketplaceAdminAttemptFailureStageResolver {

  private static final EnumSet<MarketplaceAdminReviewValidationCode> PHASE_B_PREFLIGHT =
      EnumSet.of(
          MarketplaceAdminReviewValidationCode.SERVER_SIGNER_UNAVAILABLE,
          MarketplaceAdminReviewValidationCode.RELAYER_NOT_REGISTERED,
          MarketplaceAdminReviewValidationCode.RELAYER_REGISTRATION_CHECK_FAILED,
          MarketplaceAdminReviewValidationCode.GAS_PREFLIGHT_FAILED,
          MarketplaceAdminReviewValidationCode.DRAFT_BUILD_FAILED,
          MarketplaceAdminReviewValidationCode.RPC_UNAVAILABLE,
          MarketplaceAdminReviewValidationCode.CHAIN_LOOKUP_FAILED,
          MarketplaceAdminReviewValidationCode.CHAIN_LOOKUP_TIMEOUT);

  private static final EnumSet<MarketplaceAdminReviewValidationCode> PHASE_B_CHAIN_MISMATCH =
      EnumSet.of(
          MarketplaceAdminReviewValidationCode.CHAIN_ORDER_ABSENT,
          MarketplaceAdminReviewValidationCode.CHAIN_ORDER_NOT_CREATED,
          MarketplaceAdminReviewValidationCode.CHAIN_ORDER_ALREADY_REFUNDED,
          MarketplaceAdminReviewValidationCode.CHAIN_ORDER_ALREADY_SETTLED,
          MarketplaceAdminReviewValidationCode.CONTRACT_DEADLINE_EXPIRED,
          MarketplaceAdminReviewValidationCode.CHAIN_MISMATCH_REQUIRES_SYNC);

  private static final EnumSet<MarketplaceAdminReviewValidationCode> PHASE_C_RELOCK =
      EnumSet.of(
          MarketplaceAdminReviewValidationCode.ACTIVE_EXECUTION_EXISTS,
          MarketplaceAdminReviewValidationCode.INVALID_LOCAL_STATUS,
          MarketplaceAdminReviewValidationCode.ESCROW_NOT_LOCKED,
          MarketplaceAdminReviewValidationCode.RESERVATION_NOT_USER_EIP7702);

  private static final EnumSet<MarketplaceAdminReviewValidationCode> PHASE_C_BIND =
      EnumSet.of(
          MarketplaceAdminReviewValidationCode.IDEMPOTENCY_REUSE_ATTEMPT_MISMATCH,
          MarketplaceAdminReviewValidationCode.IDEMPOTENCY_CONFLICT);

  private MarketplaceAdminAttemptFailureStageResolver() {}

  public static MarketplaceAdminAttemptFailureStage resolve(
      MarketplaceReservationActionState actionState) {
    if (actionState == null) {
      return null;
    }
    return resolve(actionState.getStatus(), actionState.getErrorCode());
  }

  public static MarketplaceAdminAttemptFailureStage resolve(
      ReservationActionStateStatus status, String errorCode) {
    if (status == null || status == ReservationActionStateStatus.CONFIRMED) {
      return null;
    }
    MarketplaceAdminReviewValidationCode code = parse(errorCode);
    if (status == ReservationActionStateStatus.TERMINATED && code != null) {
      return MarketplaceAdminAttemptFailureStage.EXECUTION_TERMINATED;
    }
    if (status == ReservationActionStateStatus.PREPARATION_FAILED) {
      if (code == MarketplaceAdminReviewValidationCode.INTENT_BIND_CONFLICT) {
        return MarketplaceAdminAttemptFailureStage.PHASE_C_SUBMIT;
      }
      if (code != null && PHASE_B_PREFLIGHT.contains(code)) {
        return MarketplaceAdminAttemptFailureStage.PHASE_B_PREFLIGHT;
      }
      if (code == MarketplaceAdminReviewValidationCode.ADMIN_PREPARATION_EXPIRED) {
        return MarketplaceAdminAttemptFailureStage.RECOVERY_EXPIRED_PREPARATION;
      }
    }
    if (status == ReservationActionStateStatus.STALE) {
      if (code != null && PHASE_B_CHAIN_MISMATCH.contains(code)) {
        return MarketplaceAdminAttemptFailureStage.PHASE_B_CHAIN_MISMATCH;
      }
      if (code != null && PHASE_C_BIND.contains(code)) {
        return MarketplaceAdminAttemptFailureStage.PHASE_C_BIND;
      }
      if (code != null && PHASE_C_RELOCK.contains(code)) {
        return MarketplaceAdminAttemptFailureStage.PHASE_C_RELOCK;
      }
    }
    return errorCode == null || errorCode.isBlank()
        ? null
        : MarketplaceAdminAttemptFailureStage.UNKNOWN;
  }

  private static MarketplaceAdminReviewValidationCode parse(String errorCode) {
    if (errorCode == null || errorCode.isBlank()) {
      return null;
    }
    try {
      return MarketplaceAdminReviewValidationCode.valueOf(errorCode);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }
}
