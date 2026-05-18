package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RecordTrainerStrikePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RunReservationPostCommitPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RunReservationTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SubmitEscrowTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.TrainerStrikeEvent;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.event.EscrowDispatchEventListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AutoCancelBatchItemProcessorTest {

  @Mock private LoadReservationPort loadReservationPort;
  @Mock private SaveReservationPort saveReservationPort;
  @Mock private SubmitEscrowTransactionPort submitEscrowTransactionPort;
  @Mock private RecordTrainerStrikePort recordTrainerStrikePort;
  @Mock private RunReservationTransactionPort runReservationTransactionPort;
  @Mock private RunReservationPostCommitPort runReservationPostCommitPort;
  @Mock private Clock clock;

  @InjectMocks private AutoCancelBatchItemProcessor sut;

  @BeforeEach
  void setUpClock() {
    given(clock.instant()).willReturn(Instant.parse("2026-05-16T00:00:00Z"));
    given(clock.getZone()).willReturn(ZoneId.of("Asia/Seoul"));
    given(
            runReservationTransactionPort.requiresNew(
                org.mockito.ArgumentMatchers.<java.util.function.Supplier<Void>>any()))
        .willAnswer(
            invocation -> {
              invocation.<java.util.function.Supplier<Void>>getArgument(0).get();
              return null;
            });
  }

  @Test
  @DisplayName(
      "process - DB 저장 먼저, 그 다음 escrow adminRefund 호출 (DB-first ordering + stale-read guard)")
  void process_DbFirstThenEscrow() {
    // Arrange: stale reservation passed in from batch read (no-lock)
    Reservation stale = org.mockito.Mockito.mock(Reservation.class);
    Long reservationId = 1L;
    given(stale.getId()).willReturn(reservationId);

    // Fresh locked reservation returned by findByIdWithLock (re-fetch guard)
    Reservation fresh = org.mockito.Mockito.mock(Reservation.class);
    String orderId = "order123";
    Long trainerId = 100L;
    given(fresh.getId()).willReturn(reservationId);
    given(fresh.getOrderId()).willReturn(orderId);
    given(fresh.getTrainerId()).willReturn(trainerId);
    given(fresh.getStatus()).willReturn(ReservationStatus.PENDING);
    given(fresh.isLegacySchedulerEligibleAt(any())).willReturn(true);

    // DB-first: timeoutCancel called with sentinel hash
    Reservation cancelledWithSentinel = org.mockito.Mockito.mock(Reservation.class);
    given(cancelledWithSentinel.getId()).willReturn(reservationId);
    given(cancelledWithSentinel.getStatus()).willReturn(ReservationStatus.TIMEOUT_CANCELLED);
    given(cancelledWithSentinel.getTxHash())
        .willReturn(EscrowDispatchEventListener.PENDING_TX_HASH);
    given(fresh.timeoutCancel(EscrowDispatchEventListener.PENDING_TX_HASH))
        .willReturn(cancelledWithSentinel);
    given(saveReservationPort.save(cancelledWithSentinel)).willReturn(cancelledWithSentinel);
    given(loadReservationPort.findByIdWithLock(reservationId))
        .willReturn(Optional.of(fresh))
        .willReturn(Optional.of(cancelledWithSentinel));

    // Escrow called after DB save
    String realTxHash = "0xhash";
    given(submitEscrowTransactionPort.submitAdminRefund(orderId)).willReturn(realTxHash);

    // txHash write-back
    Reservation cancelledWithRealTxHash = org.mockito.Mockito.mock(Reservation.class);
    given(cancelledWithSentinel.updateTxHash(realTxHash)).willReturn(cancelledWithRealTxHash);
    given(saveReservationPort.save(cancelledWithRealTxHash)).willReturn(cancelledWithRealTxHash);
    AtomicReference<Runnable> afterCommit = captureAfterCommitCallback();
    runRequiresNewImmediately();

    // Act
    sut.process(stale);

    // External mutation is deferred until the local DB transaction commits.
    then(submitEscrowTransactionPort).shouldHaveNoInteractions();
    afterCommit.get().run();

    // Assert — re-fetch with lock, then DB save before escrow call
    verify(loadReservationPort, times(2)).findByIdWithLock(reservationId);

    InOrder order = inOrder(saveReservationPort, submitEscrowTransactionPort);
    order.verify(saveReservationPort).save(cancelledWithSentinel);
    order.verify(submitEscrowTransactionPort).submitAdminRefund(orderId);
    order.verify(saveReservationPort).save(cancelledWithRealTxHash);

    verify(recordTrainerStrikePort).recordStrike(trainerId, TrainerStrikeEvent.REASON_TIMEOUT);
  }

  @Test
  @DisplayName("process - afterCommit write-back 실패는 콜백 밖으로 전파하지 않는다")
  void process_afterCommitWriteBackFailure_isLoggedAndSwallowed() {
    Reservation stale = org.mockito.Mockito.mock(Reservation.class);
    Long reservationId = 4L;
    given(stale.getId()).willReturn(reservationId);

    Reservation fresh = org.mockito.Mockito.mock(Reservation.class);
    String orderId = "order456";
    given(fresh.getId()).willReturn(reservationId);
    given(fresh.getOrderId()).willReturn(orderId);
    given(fresh.getTrainerId()).willReturn(100L);
    given(fresh.getStatus()).willReturn(ReservationStatus.PENDING);
    given(fresh.isLegacySchedulerEligibleAt(any())).willReturn(true);

    Reservation cancelledWithSentinel = org.mockito.Mockito.mock(Reservation.class);
    given(cancelledWithSentinel.getStatus()).willReturn(ReservationStatus.TIMEOUT_CANCELLED);
    given(cancelledWithSentinel.getTxHash())
        .willReturn(EscrowDispatchEventListener.PENDING_TX_HASH);
    given(fresh.timeoutCancel(EscrowDispatchEventListener.PENDING_TX_HASH))
        .willReturn(cancelledWithSentinel);
    given(saveReservationPort.save(cancelledWithSentinel)).willReturn(cancelledWithSentinel);
    given(loadReservationPort.findByIdWithLock(reservationId))
        .willReturn(Optional.of(fresh))
        .willReturn(Optional.of(cancelledWithSentinel));
    given(submitEscrowTransactionPort.submitAdminRefund(orderId)).willReturn("0xhash");
    willThrow(new IllegalStateException("db down"))
        .given(cancelledWithSentinel)
        .updateTxHash("0xhash");
    AtomicReference<Runnable> afterCommit = captureAfterCommitCallback();
    willAnswer(
            invocation -> {
              try {
                invocation.<Runnable>getArgument(0).run();
              } catch (RuntimeException ignored) {
                // infrastructure adapter owns post-commit exception isolation.
              }
              return null;
            })
        .given(runReservationPostCommitPort)
        .requiresNew(any());

    sut.process(stale);
    afterCommit.get().run();

    then(submitEscrowTransactionPort).should().submitAdminRefund(orderId);
    then(saveReservationPort).should(times(1)).save(cancelledWithSentinel);
  }

  @Test
  @DisplayName("process - PENDING이 아닌 상태이면 처리를 건너뛴다 (stale-read guard)")
  void process_skipsIfNotPending() {
    // Arrange: stale is PENDING, but fresh (locked) turns out to be USER_CANCELLED
    Reservation stale = org.mockito.Mockito.mock(Reservation.class);
    Long reservationId = 2L;
    given(stale.getId()).willReturn(reservationId);

    Reservation fresh = org.mockito.Mockito.mock(Reservation.class);
    given(fresh.getId()).willReturn(reservationId);
    given(fresh.getStatus()).willReturn(ReservationStatus.USER_CANCELLED);

    given(loadReservationPort.findByIdWithLock(reservationId)).willReturn(Optional.of(fresh));

    // Act
    sut.process(stale);

    // Assert — no DB save, no escrow call, no strike
    org.mockito.Mockito.verifyNoInteractions(
        saveReservationPort, submitEscrowTransactionPort, recordTrainerStrikePort);
  }

  @Test
  @DisplayName("process - USER_EIP7702 locked row이면 legacy auto-cancel을 건너뛴다")
  void process_skipsUserEip7702LockedRow() {
    Reservation stale = org.mockito.Mockito.mock(Reservation.class);
    Long reservationId = 3L;
    given(stale.getId()).willReturn(reservationId);

    Reservation fresh = org.mockito.Mockito.mock(Reservation.class);
    given(fresh.getId()).willReturn(reservationId);
    given(fresh.getStatus()).willReturn(ReservationStatus.PENDING);
    given(fresh.isLegacySchedulerEligibleAt(any())).willReturn(false);

    given(loadReservationPort.findByIdWithLock(reservationId)).willReturn(Optional.of(fresh));

    sut.process(stale);

    org.mockito.Mockito.verifyNoInteractions(
        saveReservationPort, submitEscrowTransactionPort, recordTrainerStrikePort);
  }

  private AtomicReference<Runnable> captureAfterCommitCallback() {
    AtomicReference<Runnable> callback = new AtomicReference<>();
    willAnswer(
            invocation -> {
              callback.set(invocation.getArgument(1, Runnable.class));
              return null;
            })
        .given(runReservationPostCommitPort)
        .afterCommit(eq("AutoCancel"), any());
    return callback;
  }

  private void runRequiresNewImmediately() {
    willAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(0).run();
              return null;
            })
        .given(runReservationPostCommitPort)
        .requiresNew(any());
  }
}
