package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionCandidateView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionStateView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionCandidatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationActionState;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationExecutionCandidateGuardTest {

  @Mock private LoadReservationExecutionStatePort loadReservationExecutionStatePort;
  @Mock private LoadReservationExecutionCandidatePort loadReservationExecutionCandidatePort;

  private ReservationExecutionCandidateGuard guard;

  @BeforeEach
  void setUp() {
    guard =
        new ReservationExecutionCandidateGuard(
            loadReservationExecutionStatePort, loadReservationExecutionCandidatePort);
  }

  @Test
  @DisplayName("current intent가 SIGNED이면 unresolved blocking execution으로 판단한다")
  void hasBlockingExecution_BlocksCurrentSignedIntent() {
    Reservation reservation = reservation();
    MarketplaceReservationActionState actionState =
        actionState(ReservationEscrowAction.PURCHASE, "attempt-1", "intent-1");
    when(loadReservationExecutionStatePort.loadState("intent-1"))
        .thenReturn(
            new ReservationExecutionStateView(
                "intent-1", "SIGNED", "MARKETPLACE_CLASS_PURCHASE", 1L));

    assertThat(guard.hasBlockingExecution(reservation, actionState)).isTrue();
  }

  @Test
  @DisplayName("payload evidence가 현재 action-state와 일치하고 transaction이 SUCCEEDED면 blocking이다")
  void hasBlockingExecution_BlocksSucceededCandidateWithMatchingEvidence() {
    Reservation reservation = reservation();
    MarketplaceReservationActionState actionState =
        actionState(ReservationEscrowAction.PURCHASE, "attempt-1", null);
    when(loadReservationExecutionCandidatePort.findByReservationResource(77L, "0xorder"))
        .thenReturn(
            List.of(
                candidate(
                    "intent-1",
                    "FAILED_ONCHAIN",
                    "MARKETPLACE_CLASS_PURCHASE",
                    "SUCCEEDED",
                    evidence(
                        77L, 88L, 99L, "attempt-1", "0xorder", "MARKETPLACE_CLASS_PURCHASE"))));

    assertThat(guard.hasBlockingExecution(reservation, actionState)).isTrue();
  }

  @Test
  @DisplayName("payload evidence가 다른 action-state를 가리키면 blocking 후보에서 제외한다")
  void hasBlockingExecution_IgnoresCandidateForDifferentAttempt() {
    Reservation reservation = reservation();
    MarketplaceReservationActionState actionState =
        actionState(ReservationEscrowAction.PURCHASE, "attempt-1", null);
    when(loadReservationExecutionCandidatePort.findByReservationResource(77L, "0xorder"))
        .thenReturn(
            List.of(
                candidate(
                    "intent-1",
                    "SIGNED",
                    "MARKETPLACE_CLASS_PURCHASE",
                    null,
                    evidence(
                        77L, 88L, 100L, "attempt-1", "0xorder", "MARKETPLACE_CLASS_PURCHASE"))));

    assertThat(guard.hasBlockingExecution(reservation, actionState)).isFalse();
  }

  @Test
  @DisplayName("payload evidence가 없거나 파싱 실패한 동일 action 후보는 fail-closed로 blocking한다")
  void hasBlockingExecution_BlocksMalformedEvidenceConservatively() {
    Reservation reservation = reservation();
    MarketplaceReservationActionState actionState =
        actionState(ReservationEscrowAction.BUYER_CONFIRM, "attempt-1", null);
    when(loadReservationExecutionCandidatePort.findByReservationResource(77L, "0xorder"))
        .thenReturn(
            List.of(
                candidate("intent-1", "PENDING_ONCHAIN", "MARKETPLACE_CLASS_CONFIRM", null, null)));

    assertThat(guard.hasBlockingExecution(reservation, actionState)).isTrue();
  }

  @Test
  @DisplayName("cancel과 trainer reject는 같은 MARKETPLACE_CLASS_CANCEL action code를 사용한다")
  void hasBlockingExecution_MapsRejectToCancelExecutionAction() {
    Reservation reservation = reservation();
    MarketplaceReservationActionState actionState =
        actionState(ReservationEscrowAction.TRAINER_REJECT, "attempt-1", null);
    when(loadReservationExecutionCandidatePort.findByReservationResource(77L, "0xorder"))
        .thenReturn(
            List.of(
                candidate(
                    "intent-1",
                    "AWAITING_SIGNATURE",
                    "MARKETPLACE_CLASS_CANCEL",
                    null,
                    evidence(77L, 88L, 99L, "attempt-1", "0xorder", "MARKETPLACE_CLASS_CANCEL"))));

    assertThat(guard.hasBlockingExecution(reservation, actionState)).isTrue();
  }

  @Test
  @DisplayName("AWAITING_SIGNATURE current intent를 취소 가능한 후보로 반환한다")
  void findAwaitingSignatureIntent_ReturnsCurrentIntentFirst() {
    Reservation reservation = reservation();
    MarketplaceReservationActionState actionState =
        actionState(ReservationEscrowAction.PURCHASE, "attempt-1", "intent-current");
    when(loadReservationExecutionStatePort.loadState("intent-current"))
        .thenReturn(
            new ReservationExecutionStateView(
                "intent-current", "AWAITING_SIGNATURE", "MARKETPLACE_CLASS_PURCHASE", 1L));

    assertThat(guard.findAwaitingSignatureIntent(reservation, actionState))
        .contains("intent-current");
  }

  @Test
  @DisplayName("action-state 없이도 reservation/order resource의 marketplace blocking 후보를 찾는다")
  void hasBlockingExecutionForAnyMarketplaceAction_BlocksOrphanMarketplaceCandidate() {
    Reservation reservation = reservation();
    when(loadReservationExecutionCandidatePort.findByReservationResource(77L, "0xorder"))
        .thenReturn(
            List.of(
                candidate(
                    "intent-1",
                    "SIGNED",
                    "MARKETPLACE_CLASS_EXPIRED_REFUND",
                    null,
                    evidence(
                        77L, 88L, null, null, "0xorder", "MARKETPLACE_CLASS_EXPIRED_REFUND"))));

    assertThat(guard.hasBlockingExecutionForAnyMarketplaceAction(reservation)).isTrue();
  }

  @Test
  @DisplayName("action-state 없는 후보 검사에서 다른 reservation evidence는 제외한다")
  void hasBlockingExecutionForAction_IgnoresDifferentReservationEvidence() {
    Reservation reservation = reservation();
    when(loadReservationExecutionCandidatePort.findByReservationResource(77L, "0xorder"))
        .thenReturn(
            List.of(
                candidate(
                    "intent-1",
                    "SIGNED",
                    "MARKETPLACE_CLASS_CONFIRM",
                    null,
                    evidence(999L, 88L, null, null, "0xother", "MARKETPLACE_CLASS_CONFIRM"))));

    assertThat(
            guard.hasBlockingExecutionForAction(reservation, ReservationEscrowAction.BUYER_CONFIRM))
        .isFalse();
  }

  @Test
  @DisplayName("admin refund action 후보도 동일 attempt evidence이면 blocking으로 판단한다")
  void hasBlockingExecution_BlocksAdminRefundCandidate() {
    Reservation reservation = reservation();
    MarketplaceReservationActionState actionState =
        actionState(ReservationEscrowAction.ADMIN_REFUND, "attempt-1", null);
    when(loadReservationExecutionCandidatePort.findByReservationResource(77L, "0xorder"))
        .thenReturn(
            List.of(
                candidate(
                    "intent-admin-refund",
                    "PENDING_ONCHAIN",
                    "MARKETPLACE_ADMIN_REFUND",
                    null,
                    evidence(77L, 88L, 99L, "attempt-1", "0xorder", "MARKETPLACE_ADMIN_REFUND"))));

    assertThat(guard.hasBlockingExecution(reservation, actionState)).isTrue();
  }

  @Test
  @DisplayName("action-state 없는 후보 검사도 admin settle action을 marketplace action으로 취급한다")
  void hasBlockingExecutionForAnyMarketplaceAction_BlocksAdminSettleCandidate() {
    Reservation reservation = reservation();
    when(loadReservationExecutionCandidatePort.findByReservationResource(77L, "0xorder"))
        .thenReturn(
            List.of(
                candidate(
                    "intent-admin-settle",
                    "CONFIRMED",
                    "MARKETPLACE_ADMIN_SETTLE",
                    null,
                    evidence(77L, 88L, null, null, "0xorder", "MARKETPLACE_ADMIN_SETTLE"))));

    assertThat(guard.hasBlockingExecutionForAnyMarketplaceAction(reservation)).isTrue();
  }

  private Reservation reservation() {
    return Reservation.builder().id(77L).orderKey("0xorder").build();
  }

  private MarketplaceReservationActionState actionState(
      ReservationEscrowAction action, String attemptToken, String executionIntentPublicId) {
    return MarketplaceReservationActionState.builder()
        .id(99L)
        .reservationId(77L)
        .escrowId(88L)
        .actionType(action)
        .attemptToken(attemptToken)
        .executionIntentPublicId(executionIntentPublicId)
        .build();
  }

  private ReservationExecutionCandidateView candidate(
      String intentId,
      String status,
      String actionType,
      String transactionStatus,
      ReservationExecutionCandidateView.PayloadEvidence evidence) {
    return new ReservationExecutionCandidateView(
        intentId,
        status,
        actionType,
        1L,
        null,
        transactionStatus,
        null,
        evidence,
        evidence != null);
  }

  private ReservationExecutionCandidateView.PayloadEvidence evidence(
      Long reservationId,
      Long escrowId,
      Long actionStateId,
      String attemptToken,
      String orderKey,
      String actionType) {
    return new ReservationExecutionCandidateView.PayloadEvidence(
        1, reservationId, escrowId, actionStateId, attemptToken, orderKey, actionType);
  }
}
