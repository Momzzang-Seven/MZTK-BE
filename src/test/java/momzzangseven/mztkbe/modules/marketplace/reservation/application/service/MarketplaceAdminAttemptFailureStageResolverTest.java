package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminReviewValidationCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.MarketplaceAdminAttemptFailureStage;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationActionStateStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MarketplaceAdminAttemptFailureStageResolverTest {

  @Test
  @DisplayName("Phase B transient preflight codes map to PHASE_B_PREFLIGHT")
  void phaseBPreflight() {
    assertThat(
            MarketplaceAdminAttemptFailureStageResolver.resolve(
                ReservationActionStateStatus.PREPARATION_FAILED,
                MarketplaceAdminReviewValidationCode.CHAIN_LOOKUP_TIMEOUT.name()))
        .isEqualTo(MarketplaceAdminAttemptFailureStage.PHASE_B_PREFLIGHT);
  }

  @Test
  @DisplayName("Phase B authoritative chain mismatch codes map to PHASE_B_CHAIN_MISMATCH")
  void phaseBChainMismatch() {
    assertThat(
            MarketplaceAdminAttemptFailureStageResolver.resolve(
                ReservationActionStateStatus.STALE,
                MarketplaceAdminReviewValidationCode.CHAIN_ORDER_ABSENT.name()))
        .isEqualTo(MarketplaceAdminAttemptFailureStage.PHASE_B_CHAIN_MISMATCH);
  }

  @Test
  @DisplayName("Phase C relock, bind, submit codes are separated")
  void phaseCMapping() {
    assertThat(
            MarketplaceAdminAttemptFailureStageResolver.resolve(
                ReservationActionStateStatus.STALE,
                MarketplaceAdminReviewValidationCode.INVALID_LOCAL_STATUS.name()))
        .isEqualTo(MarketplaceAdminAttemptFailureStage.PHASE_C_RELOCK);
    assertThat(
            MarketplaceAdminAttemptFailureStageResolver.resolve(
                ReservationActionStateStatus.STALE,
                MarketplaceAdminReviewValidationCode.PREPARED_SNAPSHOT_MISMATCH.name()))
        .isEqualTo(MarketplaceAdminAttemptFailureStage.PHASE_C_RELOCK);
    assertThat(
            MarketplaceAdminAttemptFailureStageResolver.resolve(
                ReservationActionStateStatus.STALE,
                MarketplaceAdminReviewValidationCode.IDEMPOTENCY_REUSE_ATTEMPT_MISMATCH.name()))
        .isEqualTo(MarketplaceAdminAttemptFailureStage.PHASE_C_BIND);
    assertThat(
            MarketplaceAdminAttemptFailureStageResolver.resolve(
                ReservationActionStateStatus.PREPARATION_FAILED,
                MarketplaceAdminReviewValidationCode.INTENT_BIND_CONFLICT.name()))
        .isEqualTo(MarketplaceAdminAttemptFailureStage.PHASE_C_SUBMIT);
  }

  @Test
  @DisplayName("terminated, confirmed, and unknown closed attempts map deterministically")
  void terminalMapping() {
    assertThat(
            MarketplaceAdminAttemptFailureStageResolver.resolve(
                ReservationActionStateStatus.TERMINATED,
                MarketplaceAdminReviewValidationCode.RPC_UNAVAILABLE.name()))
        .isEqualTo(MarketplaceAdminAttemptFailureStage.EXECUTION_TERMINATED);
    assertThat(
            MarketplaceAdminAttemptFailureStageResolver.resolve(
                ReservationActionStateStatus.CONFIRMED, null))
        .isNull();
    assertThat(
            MarketplaceAdminAttemptFailureStageResolver.resolve(
                ReservationActionStateStatus.STALE, "SOMETHING_NEW"))
        .isEqualTo(MarketplaceAdminAttemptFailureStage.UNKNOWN);
  }
}
