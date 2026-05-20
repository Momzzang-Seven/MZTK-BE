package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowOrderView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationCreateIdempotencyPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowOrderPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RecordTrainerStrikePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationCreateIdempotencyPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.ApplyReservationEscrowExecutionHookService;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.ReservationTestTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowFlow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.TrainerStrikeEvent;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionTransactionSummary;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionTransactionPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionReferenceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceEscrowExecutionPayload;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceTokenMovement;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceActorType;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceAllowanceStrategy;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketplaceEscrowExecutionActionHandlerAdapterTest {

  private static final String ORDER_ID = "00000000-0000-0000-0000-000000000123";
  private static final String ORDER_KEY =
      "0x0000000000000000000000000000000000000000000000000000000000000123";

  @Mock private LoadReservationPort loadReservationPort;
  @Mock private SaveReservationPort saveReservationPort;
  @Mock private RecordTrainerStrikePort recordTrainerStrikePort;
  @Mock private LoadReservationEscrowOrderPort loadReservationEscrowOrderPort;
  @Mock private LoadExecutionTransactionPort loadExecutionTransactionPort;
  @Mock private LoadReservationCreateIdempotencyPort loadReservationCreateIdempotencyPort;
  @Mock private SaveReservationCreateIdempotencyPort saveReservationCreateIdempotencyPort;

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private MarketplaceEscrowExecutionActionHandlerAdapter sut;

  @BeforeEach
  void setUp() {
    ApplyReservationEscrowExecutionHookService hookService =
        new ApplyReservationEscrowExecutionHookService(
            loadReservationPort,
            saveReservationPort,
            Clock.fixed(Instant.parse("2026-05-16T00:00:00Z"), ZoneOffset.UTC),
            recordTrainerStrikePort,
            loadReservationEscrowOrderPort,
            loadReservationCreateIdempotencyPort,
            saveReservationCreateIdempotencyPort);
    hookService.setTransactionPort(ReservationTestTransactionPort.direct());
    sut = new MarketplaceEscrowExecutionActionHandlerAdapter(objectMapper, hookService);
    sut.setLoadExecutionTransactionPort(loadExecutionTransactionPort);
  }

  @Test
  @DisplayName("confirmed purchase orphan intent syncs by reservation id and pending attempt token")
  void afterExecutionConfirmed_purchaseOrphan_syncsByPendingAttemptToken() throws Exception {
    Reservation reservation = purchasePreparing("purchase-token");
    ExecutionIntent intent = intent("intent-1", payload("purchase-token"));
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-1"))
        .willReturn(Optional.empty());
    given(loadReservationPort.findByIdWithLock(123L)).willReturn(Optional.of(reservation));

    sut.afterExecutionConfirmed(intent, null);

    ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
    then(saveReservationPort).should().save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(ReservationStatus.PENDING);
    assertThat(captor.getValue().getEscrowStatus()).isEqualTo(ReservationEscrowStatus.LOCKED);
    assertThat(captor.getValue().getCurrentExecutionIntentPublicId()).isNull();
    assertThat(captor.getValue().getPendingAction()).isNull();
    assertThat(captor.getValue().getPendingAttemptToken()).isNull();
    assertThat(captor.getValue().getContractDeadlineEpochSeconds()).isEqualTo(1_800_000_000L);
  }

  @Test
  @DisplayName("confirmed orphan intent with a stale pending token is ignored")
  void afterExecutionConfirmed_staleOrphanToken_ignored() throws Exception {
    Reservation reservation = purchasePreparing("current-token");
    ExecutionIntent intent = intent("intent-1", payload("stale-token"));
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-1"))
        .willReturn(Optional.empty());
    given(loadReservationPort.findByIdWithLock(123L)).willReturn(Optional.of(reservation));

    sut.afterExecutionConfirmed(intent, null);

    then(saveReservationPort).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("confirmed purchase marks deadline recovery when actual deadline is unsafe")
  void afterExecutionConfirmed_purchaseDeadlineUnsafe_marksRecoveryRequired() throws Exception {
    Reservation reservation = purchasePreparing("purchase-token");
    ExecutionIntent intent = intent("intent-1", payload("purchase-token", 1L, 1L));
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-1"))
        .willReturn(Optional.of(reservation.bindPurchaseIntent("intent-1")));

    sut.afterExecutionConfirmed(intent, null);

    ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
    then(saveReservationPort).should().save(captor.capture());
    assertThat(captor.getValue().getStatus())
        .isEqualTo(ReservationStatus.DEADLINE_RECOVERY_REQUIRED);
  }

  @Test
  @DisplayName("confirmed purchase uses getOrder deadline instead of the payload expected deadline")
  void afterExecutionConfirmed_purchaseUsesActualChainDeadline() throws Exception {
    Reservation reservation = purchasePreparing("purchase-token");
    ExecutionIntent intent = intent("intent-1", payload("purchase-token"));
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-1"))
        .willReturn(Optional.of(reservation.bindPurchaseIntent("intent-1")));
    given(loadReservationEscrowOrderPort.getOrder(ORDER_KEY))
        .willReturn(order(ReservationEscrowOrderView.STATE_CREATED, 1_900_000_000L));

    sut.afterExecutionConfirmed(intent, null);

    ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
    then(saveReservationPort).should().save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(ReservationStatus.PENDING);
    assertThat(captor.getValue().getContractDeadlineEpochSeconds()).isEqualTo(1_900_000_000L);
    InOrder inOrder = Mockito.inOrder(loadReservationEscrowOrderPort, loadReservationPort);
    inOrder.verify(loadReservationEscrowOrderPort).getOrder(ORDER_KEY);
    inOrder.verify(loadReservationPort).findByCurrentExecutionIntentPublicIdWithLock("intent-1");
  }

  @Test
  @DisplayName("confirmed purchase getOrder failure stores sync-required without holding the lock")
  void afterExecutionConfirmed_purchaseChainOrderReadFailure_marksDeadlineSyncRequired()
      throws Exception {
    Reservation reservation = purchasePreparing("purchase-token").bindPurchaseIntent("intent-1");
    ExecutionIntent intent = intent("intent-1", payload("purchase-token"));
    willThrow(new IllegalStateException("rpc unavailable"))
        .given(loadReservationEscrowOrderPort)
        .getOrder(ORDER_KEY);
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-1"))
        .willReturn(Optional.of(reservation));

    sut.afterExecutionConfirmed(intent, null);

    InOrder inOrder = Mockito.inOrder(loadReservationEscrowOrderPort, loadReservationPort);
    inOrder.verify(loadReservationEscrowOrderPort).getOrder(ORDER_KEY);
    inOrder.verify(loadReservationPort).findByCurrentExecutionIntentPublicIdWithLock("intent-1");
    ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
    then(saveReservationPort).should().save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(ReservationStatus.DEADLINE_SYNC_REQUIRED);
    assertThat(captor.getValue().getEscrowFailureCode()).isEqualTo("CHAIN_ORDER_READ_FAILED");
  }

  @Test
  @DisplayName("confirmed purchase syncs admin-refunded chain state without reopening PENDING")
  void afterExecutionConfirmed_purchaseChainAdminRefunded_syncsTimeoutCancelled() throws Exception {
    Reservation reservation = purchasePreparing("purchase-token");
    ExecutionIntent intent = intent("intent-1", payload("purchase-token"));
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-1"))
        .willReturn(Optional.of(reservation.bindPurchaseIntent("intent-1")));
    given(loadReservationEscrowOrderPort.getOrder(ORDER_KEY))
        .willReturn(order(ReservationEscrowOrderView.STATE_ADMIN_REFUNDED, 1_900_000_000L));

    sut.afterExecutionConfirmed(intent, null);

    ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
    then(saveReservationPort).should().save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(ReservationStatus.TIMEOUT_CANCELLED);
    assertThat(captor.getValue().getContractDeadlineEpochSeconds()).isEqualTo(1_900_000_000L);
  }

  @ParameterizedTest(name = "state={0} -> status={1}, escrowStatus={2}")
  @CsvSource({
    "2000, SETTLED, SETTLED",
    "3000, MANUAL_SYNC_REQUIRED, MANUAL_SYNC_REQUIRED",
    "4000, AUTO_SETTLED, SETTLED",
    "6000, DEADLINE_REFUNDED, DEADLINE_REFUNDED"
  })
  @DisplayName("confirmed purchase terminal chain state syncs to the matching local outcome")
  void afterExecutionConfirmed_purchaseTerminalChainState_syncsLocalOutcome(
      int chainState,
      ReservationStatus expectedStatus,
      ReservationEscrowStatus expectedEscrowStatus)
      throws Exception {
    Reservation reservation = purchasePreparing("purchase-token");
    ExecutionIntent intent = intent("intent-1", payload("purchase-token"));
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-1"))
        .willReturn(Optional.of(reservation.bindPurchaseIntent("intent-1")));
    given(loadReservationEscrowOrderPort.getOrder(ORDER_KEY))
        .willReturn(order(chainState, 1_900_000_000L));

    sut.afterExecutionConfirmed(intent, null);

    ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
    then(saveReservationPort).should().save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(expectedStatus);
    assertThat(captor.getValue().getEscrowStatus()).isEqualTo(expectedEscrowStatus);
    assertThat(captor.getValue().getCurrentExecutionIntentPublicId()).isNull();
    assertThat(captor.getValue().getPendingAction()).isNull();
    assertThat(captor.getValue().getPendingAttemptToken()).isNull();
  }

  @Test
  @DisplayName("confirmed purchase with absent getOrder result moves to deadline sync required")
  void afterExecutionConfirmed_purchaseChainOrderAbsent_marksDeadlineSyncRequired()
      throws Exception {
    Reservation reservation = purchasePreparing("purchase-token");
    ExecutionIntent intent = intent("intent-1", payload("purchase-token"));
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-1"))
        .willReturn(Optional.of(reservation.bindPurchaseIntent("intent-1")));
    given(loadReservationEscrowOrderPort.getOrder(ORDER_KEY))
        .willReturn(
            new ReservationEscrowOrderView(
                ORDER_KEY,
                "0",
                "0x0000000000000000000000000000000000000000",
                0L,
                ReservationEscrowOrderView.STATE_ABSENT,
                "0x0000000000000000000000000000000000000000",
                "0x0000000000000000000000000000000000000000"));

    sut.afterExecutionConfirmed(intent, null);

    ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
    then(saveReservationPort).should().save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(ReservationStatus.DEADLINE_SYNC_REQUIRED);
  }

  @Test
  @DisplayName("action plan maps refund-like marketplace actions to SERVER_TO_USER reference type")
  void buildActionPlan_refundLikeActions_useServerToUserReferenceType() throws Exception {
    ExecutionActionPlan buyerCancelPlan =
        sut.buildActionPlan(
            intent(
                "intent-cancel",
                payload(
                    "cancel-token",
                    MarketplaceExecutionActionType.MARKETPLACE_CLASS_CANCEL,
                    MarketplaceActorType.BUYER)));
    ExecutionActionPlan trainerRejectPlan =
        sut.buildActionPlan(
            intent(
                "intent-reject",
                payload(
                    "reject-token",
                    MarketplaceExecutionActionType.MARKETPLACE_CLASS_CANCEL,
                    MarketplaceActorType.TRAINER)));
    ExecutionActionPlan deadlineRefundPlan =
        sut.buildActionPlan(
            intent(
                "intent-refund",
                payload(
                    "refund-token",
                    MarketplaceExecutionActionType.MARKETPLACE_CLASS_EXPIRED_REFUND,
                    MarketplaceActorType.BUYER)));

    assertThat(buyerCancelPlan.referenceType()).isEqualTo(ExecutionReferenceType.SERVER_TO_USER);
    assertThat(trainerRejectPlan.referenceType()).isEqualTo(ExecutionReferenceType.SERVER_TO_USER);
    assertThat(deadlineRefundPlan.referenceType()).isEqualTo(ExecutionReferenceType.SERVER_TO_USER);
  }

  @ParameterizedTest
  @EnumSource(MarketplaceExecutionActionType.class)
  @DisplayName("marketplace runtime action plan call uses the exact payload call target and data")
  void buildActionPlan_usesPayloadCallTargetAndData(MarketplaceExecutionActionType actionType)
      throws Exception {
    MarketplaceEscrowExecutionPayload payload =
        payload("attempt-token", actionType, MarketplaceActorType.BUYER);

    ExecutionActionPlan actionPlan =
        sut.buildActionPlan(
            intent("intent-action-plan", ExecutionActionType.valueOf(actionType.name()), payload));

    assertThat(actionPlan.calls())
        .containsExactly(
            new ExecutionDraftCall(payload.callTarget(), BigInteger.ZERO, payload.callData()));
  }

  @Test
  @DisplayName(
      "confirmed trainer reject records a source-idempotent trainer strike after local reject")
  void afterExecutionConfirmed_trainerReject_recordsSourceIdempotentStrike() throws Exception {
    Reservation reservation = rejectPending();
    MarketplaceEscrowExecutionPayload payload =
        payload(
            "reject-token",
            MarketplaceExecutionActionType.MARKETPLACE_CLASS_CANCEL,
            MarketplaceActorType.TRAINER);
    ExecutionIntent intent =
        intent("intent-reject", ExecutionActionType.MARKETPLACE_CLASS_CANCEL, payload);
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-reject"))
        .willReturn(Optional.of(reservation));

    sut.afterExecutionConfirmed(intent, null);

    ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
    then(saveReservationPort).should().save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(ReservationStatus.REJECTED);
    assertThat(captor.getValue().getEscrowStatus()).isEqualTo(ReservationEscrowStatus.REFUNDED);
    assertThat(captor.getValue().getCurrentExecutionIntentPublicId()).isNull();
    assertThat(captor.getValue().getPendingAction()).isNull();
    assertThat(captor.getValue().getPendingAttemptToken()).isNull();
    assertThat(captor.getValue().getRejectionReason()).isEqualTo("일정 불가");
    then(recordTrainerStrikePort)
        .should()
        .recordStrike(
            9L,
            TrainerStrikeEvent.REASON_REJECT,
            RecordTrainerStrikePort.SOURCE_MARKETPLACE_RESERVATION_REJECT,
            "123");
  }

  @Test
  @DisplayName("confirmed trainer reject는 strike 기록 실패와 무관하게 local REJECTED를 저장한다")
  void afterExecutionConfirmed_trainerReject_strikeFailureDoesNotRollbackReservation()
      throws Exception {
    Reservation reservation = rejectPending();
    MarketplaceEscrowExecutionPayload payload =
        payload(
            "reject-token",
            MarketplaceExecutionActionType.MARKETPLACE_CLASS_CANCEL,
            MarketplaceActorType.TRAINER);
    ExecutionIntent intent =
        intent("intent-reject", ExecutionActionType.MARKETPLACE_CLASS_CANCEL, payload);
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-reject"))
        .willReturn(Optional.of(reservation));
    willThrow(new IllegalStateException("strike failed"))
        .given(recordTrainerStrikePort)
        .recordStrike(
            9L,
            TrainerStrikeEvent.REASON_REJECT,
            RecordTrainerStrikePort.SOURCE_MARKETPLACE_RESERVATION_REJECT,
            "123");

    sut.afterExecutionConfirmed(intent, null);

    ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
    then(saveReservationPort).should().save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(ReservationStatus.REJECTED);
    assertThat(captor.getValue().getEscrowStatus()).isEqualTo(ReservationEscrowStatus.REFUNDED);
    then(recordTrainerStrikePort)
        .should()
        .recordStrike(
            9L,
            TrainerStrikeEvent.REASON_REJECT,
            RecordTrainerStrikePort.SOURCE_MARKETPLACE_RESERVATION_REJECT,
            "123");
  }

  @Test
  @DisplayName(
      "confirmed terminal actions persist the submitted on-chain tx hash, not the intent id")
  void afterExecutionConfirmed_terminalAction_persistsSubmittedTxHash() throws Exception {
    String txHash = "0x" + "1".repeat(64);
    Reservation reservation = rejectPending();
    MarketplaceEscrowExecutionPayload payload =
        payload(
            "reject-token",
            MarketplaceExecutionActionType.MARKETPLACE_CLASS_CANCEL,
            MarketplaceActorType.TRAINER);
    ExecutionIntent intent =
        intent("intent-reject", ExecutionActionType.MARKETPLACE_CLASS_CANCEL, payload).toBuilder()
            .submittedTxId(55L)
            .build();
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-reject"))
        .willReturn(Optional.of(reservation));
    given(loadExecutionTransactionPort.findById(55L))
        .willReturn(
            Optional.of(
                new ExecutionTransactionSummary(
                    55L, ExecutionTransactionStatus.SUCCEEDED, txHash)));

    sut.afterExecutionConfirmed(intent, null);

    ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
    then(saveReservationPort).should().save(captor.capture());
    assertThat(captor.getValue().getTxHash()).isEqualTo(txHash);
  }

  @Test
  @DisplayName(
      "confirmed deadline refund marks reservation DEADLINE_REFUNDED and clears pending state")
  void afterExecutionConfirmed_deadlineRefund_marksDeadlineRefunded() throws Exception {
    String txHash = "0x" + "3".repeat(64);
    Reservation reservation = deadlineRefundPending();
    MarketplaceEscrowExecutionPayload payload =
        payload(
            "refund-token",
            MarketplaceExecutionActionType.MARKETPLACE_CLASS_EXPIRED_REFUND,
            MarketplaceActorType.BUYER);
    ExecutionIntent intent =
        intent("intent-refund", ExecutionActionType.MARKETPLACE_CLASS_EXPIRED_REFUND, payload)
            .toBuilder()
            .submittedTxId(57L)
            .build();
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-refund"))
        .willReturn(Optional.of(reservation));
    given(loadExecutionTransactionPort.findById(57L))
        .willReturn(
            Optional.of(
                new ExecutionTransactionSummary(
                    57L, ExecutionTransactionStatus.SUCCEEDED, txHash)));

    sut.afterExecutionConfirmed(intent, null);

    ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
    then(saveReservationPort).should().save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(ReservationStatus.DEADLINE_REFUNDED);
    assertThat(captor.getValue().getEscrowStatus())
        .isEqualTo(ReservationEscrowStatus.DEADLINE_REFUNDED);
    assertThat(captor.getValue().getCurrentExecutionIntentPublicId()).isNull();
    assertThat(captor.getValue().getPendingAction()).isNull();
    assertThat(captor.getValue().getPendingAttemptToken()).isNull();
    assertThat(captor.getValue().getTxHash()).isEqualTo(txHash);
  }

  @Test
  @DisplayName("terminated non-purchase marketplace intent rolls reservation back to prior state")
  void afterExecutionTerminated_nonPurchase_rollsBackToPriorState() throws Exception {
    Reservation reservation = cancelPending();
    MarketplaceEscrowExecutionPayload payload =
        payload(
            "cancel-token",
            MarketplaceExecutionActionType.MARKETPLACE_CLASS_CANCEL,
            MarketplaceActorType.BUYER);
    ExecutionIntent intent =
        intent("intent-cancel", ExecutionActionType.MARKETPLACE_CLASS_CANCEL, payload);
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-cancel"))
        .willReturn(Optional.of(reservation));

    sut.afterExecutionTerminated(intent, null, ExecutionIntentStatus.FAILED_ONCHAIN, "REVERTED");

    ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
    then(saveReservationPort).should().save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(ReservationStatus.PENDING);
    assertThat(captor.getValue().getEscrowStatus()).isEqualTo(ReservationEscrowStatus.LOCKED);
    assertThat(captor.getValue().getCurrentExecutionIntentPublicId()).isNull();
    assertThat(captor.getValue().getPendingAction()).isNull();
    assertThat(captor.getValue().getPendingAttemptToken()).isNull();
    then(saveReservationCreateIdempotencyPort).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("confirmed replay repairs tx hash when the local outcome was already applied")
  void afterExecutionConfirmed_alreadyApplied_repairTxHash() throws Exception {
    String txHash = "0x" + "2".repeat(64);
    Reservation reservation =
        purchasePreparing("purchase-token")
            .markPurchaseConfirmedLocked(1_800_000_000L, LocalDateTime.of(2027, 1, 15, 8, 0));
    ExecutionIntent intent =
        intent(
                "intent-1",
                ExecutionActionType.MARKETPLACE_CLASS_PURCHASE,
                payload("purchase-token"))
            .toBuilder()
            .submittedTxId(56L)
            .build();
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-1"))
        .willReturn(Optional.empty());
    given(loadReservationPort.findByIdWithLock(123L)).willReturn(Optional.of(reservation));
    given(loadExecutionTransactionPort.findById(56L))
        .willReturn(
            Optional.of(
                new ExecutionTransactionSummary(
                    56L, ExecutionTransactionStatus.SUCCEEDED, txHash)));

    sut.afterExecutionConfirmed(intent, null);

    ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
    then(saveReservationPort).should().save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(ReservationStatus.PENDING);
    assertThat(captor.getValue().getTxHash()).isEqualTo(txHash);
  }

  @Test
  @DisplayName("retryable terminated purchase keeps reservation and create idempotency recoverable")
  void afterExecutionTerminated_retryablePurchase_keepsIdempotencyRecoverable() throws Exception {
    Reservation reservation = purchasePreparing("purchase-token").bindPurchaseIntent("intent-1");
    ExecutionIntent intent = intent("intent-1", payload("purchase-token"));
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-1"))
        .willReturn(Optional.of(reservation));

    sut.afterExecutionTerminated(intent, null, ExecutionIntentStatus.FAILED_ONCHAIN, "REVERTED");

    then(saveReservationPort).shouldHaveNoInteractions();
    then(loadReservationCreateIdempotencyPort).shouldHaveNoInteractions();
    then(saveReservationCreateIdempotencyPort).shouldHaveNoInteractions();
  }

  private Reservation purchasePreparing(String pendingAttemptToken) {
    return Reservation.createPending(
            7L,
            9L,
            11L,
            LocalDate.of(2026, 5, 20),
            LocalTime.of(10, 0),
            60,
            null,
            ORDER_ID,
            null,
            50_000,
            "PT")
        .toBuilder()
        .id(123L)
        .version(0L)
        .build()
        .beginPurchasePreparing(
            "key",
            "payload",
            LocalDateTime.of(2026, 5, 16, 1, 0),
            ORDER_KEY,
            "0x1111111111111111111111111111111111111111",
            "0x2222222222222222222222222222222222222222",
            "0x3333333333333333333333333333333333333333",
            "50000",
            1_800_000_000L,
            LocalDateTime.of(2027, 1, 15, 8, 0),
            pendingAttemptToken);
  }

  private ExecutionIntent intent(String publicId, MarketplaceEscrowExecutionPayload payload)
      throws Exception {
    return intent(publicId, ExecutionActionType.MARKETPLACE_CLASS_PURCHASE, payload);
  }

  private ExecutionIntent intent(
      String publicId, ExecutionActionType actionType, MarketplaceEscrowExecutionPayload payload)
      throws Exception {
    return ExecutionIntent.builder()
        .publicId(publicId)
        .actionType(actionType)
        .status(ExecutionIntentStatus.CONFIRMED)
        .payloadSnapshotJson(objectMapper.writeValueAsString(payload))
        .build();
  }

  private MarketplaceEscrowExecutionPayload payload(String pendingAttemptToken) {
    return payload(pendingAttemptToken, 1_800_000_000L, null);
  }

  private MarketplaceEscrowExecutionPayload payload(
      String pendingAttemptToken,
      MarketplaceExecutionActionType actionType,
      MarketplaceActorType actorType) {
    return payload(pendingAttemptToken, 1_800_000_000L, null, actionType, actorType);
  }

  private MarketplaceEscrowExecutionPayload payload(
      String pendingAttemptToken,
      Long expectedDeadlineEpochSeconds,
      Long contractDeadlineEpochSeconds) {
    return payload(
        pendingAttemptToken,
        expectedDeadlineEpochSeconds,
        contractDeadlineEpochSeconds,
        MarketplaceExecutionActionType.MARKETPLACE_CLASS_PURCHASE,
        MarketplaceActorType.BUYER);
  }

  private MarketplaceEscrowExecutionPayload payload(
      String pendingAttemptToken,
      Long expectedDeadlineEpochSeconds,
      Long contractDeadlineEpochSeconds,
      MarketplaceExecutionActionType actionType,
      MarketplaceActorType actorType) {
    return new MarketplaceEscrowExecutionPayload(
        actionType,
        actorType,
        123L,
        "123",
        ORDER_ID,
        ORDER_KEY,
        7L,
        7L,
        9L,
        7L,
        9L,
        0L,
        "PURCHASE_PREPARING",
        "PURCHASE_PREPARING",
        "0x1111111111111111111111111111111111111111",
        "0x2222222222222222222222222222222222222222",
        "0x3333333333333333333333333333333333333333",
        BigInteger.valueOf(50_000),
        MarketplaceAllowanceStrategy.PRE_EXISTING_ALLOWANCE,
        LocalDateTime.of(2026, 5, 20, 11, 0),
        expectedDeadlineEpochSeconds,
        contractDeadlineEpochSeconds,
        pendingAttemptToken,
        "PENDING",
        "0x4444444444444444444444444444444444444444",
        "0x1234",
        new MarketplaceTokenMovement(
            "0x3333333333333333333333333333333333333333",
            BigInteger.valueOf(50_000),
            "BUYER",
            "0x1111111111111111111111111111111111111111",
            "ESCROW",
            "0x4444444444444444444444444444444444444444"),
        1_700_000_000L,
        "0x" + "a".repeat(130));
  }

  private ReservationEscrowOrderView order(int state, long deadlineEpochSeconds) {
    return new ReservationEscrowOrderView(
        ORDER_KEY,
        "50000",
        "0x3333333333333333333333333333333333333333",
        deadlineEpochSeconds,
        state,
        "0x1111111111111111111111111111111111111111",
        "0x2222222222222222222222222222222222222222");
  }

  private Reservation rejectPending() {
    return Reservation.createPending(
            7L,
            9L,
            11L,
            LocalDate.of(2026, 5, 20),
            LocalTime.of(10, 0),
            60,
            null,
            ORDER_ID,
            null,
            50_000,
            "PT")
        .toBuilder()
        .id(123L)
        .version(0L)
        .orderKey(ORDER_KEY)
        .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
        .escrowStatus(ReservationEscrowStatus.LOCKED)
        .build()
        .beginRejectPending("reject-token", "일정 불가")
        .bindPendingExecutionIntent("intent-reject");
  }

  private Reservation cancelPending() {
    return baseLockedReservation()
        .beginCancelPending("cancel-token")
        .bindPendingExecutionIntent("intent-cancel");
  }

  private Reservation deadlineRefundPending() {
    return baseLockedReservation()
        .markDeadlineRefundAvailable(1_800_000_000L, LocalDateTime.of(2027, 1, 15, 8, 0))
        .beginDeadlineRefundPending("refund-token")
        .bindPendingExecutionIntent("intent-refund");
  }

  private Reservation baseLockedReservation() {
    return Reservation.createPending(
            7L,
            9L,
            11L,
            LocalDate.of(2026, 5, 20),
            LocalTime.of(10, 0),
            60,
            null,
            ORDER_ID,
            null,
            50_000,
            "PT")
        .toBuilder()
        .id(123L)
        .version(0L)
        .orderKey(ORDER_KEY)
        .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
        .escrowStatus(ReservationEscrowStatus.LOCKED)
        .build();
  }
}
