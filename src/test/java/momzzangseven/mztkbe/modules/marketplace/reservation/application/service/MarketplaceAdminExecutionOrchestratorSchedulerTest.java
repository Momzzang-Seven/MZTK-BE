package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceReservationStateException;
import momzzangseven.mztkbe.global.error.web3.Web3TransferException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminRefundReasonCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminReviewValidationCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareMarketplaceAdminEscrowCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationAdminExecutionDraft;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowOrderView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.SubmitMarketplaceAdminEscrowResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.BindReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.BuildMarketplaceAdminReservationExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowOrderPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SubmitMarketplaceAdminReservationExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationActionState;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationEscrow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationActionRequestSource;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationActionStateStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowActorType;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowFlow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketplaceAdminExecutionOrchestratorSchedulerTest {

  @Mock private LoadReservationPort loadReservationPort;
  @Mock private SaveReservationPort saveReservationPort;
  @Mock private LoadReservationEscrowPort loadReservationEscrowPort;
  @Mock private LoadReservationEscrowOrderPort loadReservationEscrowOrderPort;
  @Mock private SaveReservationEscrowPort saveReservationEscrowPort;
  @Mock private LoadReservationActionStatePort loadReservationActionStatePort;
  @Mock private SaveReservationActionStatePort saveReservationActionStatePort;
  @Mock private BindReservationActionStatePort bindReservationActionStatePort;
  @Mock private BuildMarketplaceAdminReservationExecutionPort buildExecutionPort;
  @Mock private SubmitMarketplaceAdminReservationExecutionPort submitExecutionPort;

  private MarketplaceAdminExecutionOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    orchestrator =
        new MarketplaceAdminExecutionOrchestrator(
            loadReservationPort,
            saveReservationPort,
            loadReservationEscrowPort,
            loadReservationEscrowOrderPort,
            saveReservationEscrowPort,
            loadReservationActionStatePort,
            saveReservationActionStatePort,
            bindReservationActionStatePort,
            buildExecutionPort,
            submitExecutionPort,
            null,
            null,
            ReservationTestTransactionPort.direct(),
            Clock.fixed(Instant.parse("2026-05-21T12:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void phaseBAlreadyRefundedOrderSyncsTerminalInsteadOfRollback() {
    AtomicReference<Reservation> reservationRef = new AtomicReference<>(pendingReservation());
    AtomicReference<MarketplaceReservationEscrow> escrowRef = new AtomicReference<>(escrow());
    AtomicReference<MarketplaceReservationActionState> actionStateRef = new AtomicReference<>();
    given(loadReservationPort.findByIdWithLock(1L))
        .willAnswer(ignored -> Optional.of(reservationRef.get()));
    given(saveReservationPort.save(any()))
        .willAnswer(
            invocation -> {
              Reservation saved = invocation.getArgument(0);
              reservationRef.set(saved);
              return saved;
            });
    given(loadReservationEscrowPort.findByReservationIdWithLock(1L))
        .willAnswer(ignored -> Optional.of(escrowRef.get()));
    given(saveReservationEscrowPort.save(any()))
        .willAnswer(
            invocation -> {
              MarketplaceReservationEscrow saved = invocation.getArgument(0);
              escrowRef.set(saved);
              return saved;
            });
    given(loadReservationActionStatePort.findLatestByReservationIdWithLock(1L))
        .willReturn(Optional.empty());
    given(saveReservationActionStatePort.save(any()))
        .willAnswer(
            invocation -> {
              MarketplaceReservationActionState saved =
                  invocation.<MarketplaceReservationActionState>getArgument(0).toBuilder()
                      .id(20L)
                      .build();
              actionStateRef.set(saved);
              return saved;
            });
    given(loadReservationActionStatePort.findByIdWithLock(20L))
        .willAnswer(ignored -> Optional.of(actionStateRef.get()));
    given(loadReservationEscrowOrderPort.getOrder(anyString()))
        .willReturn(orderWithState(ReservationEscrowOrderView.STATE_ADMIN_REFUNDED));

    assertThatThrownBy(
            () ->
                orchestrator.executeSchedulerRefund(
                    "scheduler-run-1", MarketplaceAdminRefundReasonCode.TRAINER_TIMEOUT, 1L))
        .isInstanceOf(MarketplaceReservationStateException.class);

    assertThat(reservationRef.get().getStatus()).isEqualTo(ReservationStatus.TIMEOUT_CANCELLED);
    assertThat(reservationRef.get().getEffectiveEscrowStatus())
        .isEqualTo(ReservationEscrowStatus.REFUNDED);
    assertThat(actionStateRef.get().getStatus()).isEqualTo(ReservationActionStateStatus.STALE);
    assertThat(actionStateRef.get().getErrorCode()).isEqualTo("CHAIN_ORDER_ALREADY_REFUNDED");
    then(buildExecutionPort).shouldHaveNoInteractions();
  }

  @Test
  void phaseCIdempotencyConflictClosesAttemptAsStaleWithAdminErrorCode() {
    AtomicReference<Reservation> reservationRef = new AtomicReference<>(pendingReservation());
    AtomicReference<MarketplaceReservationEscrow> escrowRef = new AtomicReference<>(escrow());
    AtomicReference<MarketplaceReservationActionState> actionStateRef = new AtomicReference<>();
    ReservationAdminExecutionDraft draft = new ReservationAdminExecutionDraft() {};
    given(loadReservationPort.findByIdWithLock(1L))
        .willAnswer(ignored -> Optional.of(reservationRef.get()));
    given(saveReservationPort.save(any()))
        .willAnswer(
            invocation -> {
              Reservation saved = invocation.getArgument(0);
              reservationRef.set(saved);
              return saved;
            });
    given(loadReservationEscrowPort.findByReservationIdWithLock(1L))
        .willAnswer(ignored -> Optional.of(escrowRef.get()));
    given(saveReservationEscrowPort.save(any()))
        .willAnswer(
            invocation -> {
              MarketplaceReservationEscrow saved = invocation.getArgument(0);
              escrowRef.set(saved);
              return saved;
            });
    given(loadReservationActionStatePort.findLatestByReservationIdWithLock(1L))
        .willReturn(Optional.empty());
    given(saveReservationActionStatePort.save(any()))
        .willAnswer(
            invocation -> {
              MarketplaceReservationActionState saved =
                  invocation.<MarketplaceReservationActionState>getArgument(0).toBuilder()
                      .id(20L)
                      .build();
              actionStateRef.set(saved);
              return saved;
            });
    given(loadReservationActionStatePort.findByIdWithLock(20L))
        .willAnswer(ignored -> Optional.of(actionStateRef.get()));
    given(loadReservationEscrowOrderPort.getOrder(anyString())).willReturn(createdOrder());
    given(buildExecutionPort.buildRefund(any())).willReturn(draft);
    given(submitExecutionPort.submit(draft))
        .willThrow(new Web3TransferException(ErrorCode.IDEMPOTENCY_CONFLICT, false));

    assertThatThrownBy(
            () ->
                orchestrator.executeSchedulerRefund(
                    "scheduler-run-1", MarketplaceAdminRefundReasonCode.TRAINER_TIMEOUT, 1L))
        .isInstanceOf(Web3TransferException.class);

    assertThat(reservationRef.get().getStatus()).isEqualTo(ReservationStatus.PENDING);
    assertThat(actionStateRef.get().getStatus()).isEqualTo(ReservationActionStateStatus.STALE);
    assertThat(actionStateRef.get().getRetryable()).isFalse();
    assertThat(actionStateRef.get().getErrorCode())
        .isEqualTo(MarketplaceAdminReviewValidationCode.IDEMPOTENCY_CONFLICT.name());
    then(bindReservationActionStatePort).shouldHaveNoInteractions();
  }

  @Test
  void schedulerRefundCreatesSystemActionStateAndSchedulerPayloadCommand() {
    AtomicReference<Reservation> reservationRef = new AtomicReference<>(pendingReservation());
    AtomicReference<MarketplaceReservationEscrow> escrowRef = new AtomicReference<>(escrow());
    AtomicReference<MarketplaceReservationActionState> actionStateRef = new AtomicReference<>();
    ReservationAdminExecutionDraft draft = new ReservationAdminExecutionDraft() {};
    given(loadReservationPort.findByIdWithLock(1L))
        .willAnswer(ignored -> Optional.of(reservationRef.get()));
    given(saveReservationPort.save(any()))
        .willAnswer(
            invocation -> {
              Reservation saved = invocation.getArgument(0);
              reservationRef.set(saved);
              return saved;
            });
    given(loadReservationEscrowPort.findByReservationIdWithLock(1L))
        .willAnswer(ignored -> Optional.of(escrowRef.get()));
    given(saveReservationEscrowPort.save(any()))
        .willAnswer(
            invocation -> {
              MarketplaceReservationEscrow saved = invocation.getArgument(0);
              escrowRef.set(saved);
              return saved;
            });
    given(loadReservationActionStatePort.findLatestByReservationIdWithLock(1L))
        .willReturn(Optional.empty());
    given(saveReservationActionStatePort.save(any()))
        .willAnswer(
            invocation -> {
              MarketplaceReservationActionState saved =
                  invocation.<MarketplaceReservationActionState>getArgument(0).toBuilder()
                      .id(20L)
                      .build();
              actionStateRef.set(saved);
              return saved;
            });
    given(loadReservationActionStatePort.findByIdWithLock(20L))
        .willAnswer(ignored -> Optional.of(actionStateRef.get()));
    given(buildExecutionPort.buildRefund(any())).willReturn(draft);
    given(submitExecutionPort.submit(draft))
        .willReturn(
            new SubmitMarketplaceAdminEscrowResult(
                "intent-1",
                "AWAITING_SIGNATURE",
                "EIP1559",
                LocalDateTime.of(2026, 5, 21, 12, 10),
                false));
    given(bindReservationActionStatePort.bindExecutionIntent(eq(20L), anyString(), eq("intent-1")))
        .willAnswer(
            ignored ->
                Optional.of(
                    actionStateRef.get().toBuilder()
                        .status(ReservationActionStateStatus.INTENT_BOUND)
                        .executionIntentPublicId("intent-1")
                        .build()));
    given(loadReservationEscrowOrderPort.getOrder(anyString())).willReturn(createdOrder());

    var result =
        orchestrator.executeSchedulerRefund(
            "scheduler-run-1", MarketplaceAdminRefundReasonCode.TRAINER_TIMEOUT, 1L);

    assertThat(result.actionType()).isEqualTo("MARKETPLACE_ADMIN_REFUND");
    MarketplaceReservationActionState actionState = actionStateRef.get();
    assertThat(actionState.getRequestSource()).isEqualTo(ReservationActionRequestSource.SCHEDULER);
    assertThat(actionState.getActorType()).isEqualTo(ReservationEscrowActorType.SYSTEM);
    assertThat(actionState.getActorUserId()).isNull();
    assertThat(actionState.getMemo()).isNull();
    assertThat(actionState.getReasonCode()).isEqualTo("TRAINER_TIMEOUT");

    ArgumentCaptor<PrepareMarketplaceAdminEscrowCommand> commandCaptor =
        ArgumentCaptor.forClass(PrepareMarketplaceAdminEscrowCommand.class);
    then(buildExecutionPort).should().buildRefund(commandCaptor.capture());
    assertThat(commandCaptor.getValue().requestSource())
        .isEqualTo(ReservationActionRequestSource.SCHEDULER);
    assertThat(commandCaptor.getValue().operatorUserId()).isNull();
    assertThat(commandCaptor.getValue().schedulerRunId()).isEqualTo("scheduler-run-1");
    assertThat(commandCaptor.getValue().memo()).isNull();
  }

  private Reservation pendingReservation() {
    return Reservation.builder()
        .id(1L)
        .userId(10L)
        .trainerId(20L)
        .slotId(30L)
        .reservationDate(LocalDate.of(2026, 5, 23))
        .reservationTime(LocalTime.of(10, 0))
        .durationMinutes(60)
        .status(ReservationStatus.PENDING)
        .escrowStatus(ReservationEscrowStatus.LOCKED)
        .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
        .orderId("123e4567-e89b-12d3-a456-426614174000")
        .orderKey("0x00000000000000000000000000000000123e4567e89b12d3a456426614174000")
        .buyerWalletAddress("0x1111111111111111111111111111111111111111")
        .trainerWalletAddress("0x2222222222222222222222222222222222222222")
        .tokenAddress("0x3333333333333333333333333333333333333333")
        .priceBaseUnits("50000")
        .bookedPriceAmount(50_000)
        .createdAt(LocalDateTime.of(2026, 5, 18, 10, 0))
        .version(7L)
        .build();
  }

  private MarketplaceReservationEscrow escrow() {
    return MarketplaceReservationEscrow.builder()
        .id(900L)
        .reservationId(1L)
        .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
        .escrowStatus(ReservationEscrowStatus.LOCKED)
        .orderKey("0x00000000000000000000000000000000123e4567e89b12d3a456426614174000")
        .priceBaseUnits(BigInteger.valueOf(50_000))
        .build();
  }

  private ReservationEscrowOrderView createdOrder() {
    return orderWithState(ReservationEscrowOrderView.STATE_CREATED);
  }

  private ReservationEscrowOrderView orderWithState(int state) {
    return new ReservationEscrowOrderView(
        "0x00000000000000000000000000000000123e4567e89b12d3a456426614174000",
        "50000",
        "0x3333333333333333333333333333333333333333",
        Instant.parse("2026-05-22T12:00:00Z").getEpochSecond(),
        state,
        "0x1111111111111111111111111111111111111111",
        "0x2222222222222222222222222222222222222222");
  }
}
