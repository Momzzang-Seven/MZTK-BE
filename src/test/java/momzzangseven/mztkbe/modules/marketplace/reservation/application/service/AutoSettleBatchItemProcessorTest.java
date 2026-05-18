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
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RunReservationPostCommitPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SubmitEscrowTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
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
class AutoSettleBatchItemProcessorTest {

  @Mock private LoadReservationPort loadReservationPort;
  @Mock private SaveReservationPort saveReservationPort;
  @Mock private SubmitEscrowTransactionPort submitEscrowTransactionPort;
  @Mock private RunReservationPostCommitPort runReservationPostCommitPort;
  @Mock private Clock clock;

  @InjectMocks private AutoSettleBatchItemProcessor sut;

  @BeforeEach
  void setUpClock() {
    given(clock.instant()).willReturn(Instant.parse("2026-05-16T00:00:00Z"));
    given(clock.getZone()).willReturn(ZoneId.of("Asia/Seoul"));
  }

  @Test
  @DisplayName(
      "process - DB 저장 먼저, 그 다음 escrow adminSettle 호출 (DB-first ordering + stale-read guard)")
  void process_DbFirstThenEscrow() {
    // Arrange: stale reservation passed in from batch read (no-lock)
    Reservation stale = org.mockito.Mockito.mock(Reservation.class);
    Long reservationId = 1L;
    given(stale.getId()).willReturn(reservationId);

    // Fresh locked reservation returned by findByIdWithLock
    Reservation fresh = org.mockito.Mockito.mock(Reservation.class);
    String orderId = "order123";
    given(fresh.getId()).willReturn(reservationId);
    given(fresh.getOrderId()).willReturn(orderId);
    given(fresh.getTrainerId()).willReturn(100L);
    given(fresh.getStatus()).willReturn(ReservationStatus.APPROVED);
    given(fresh.isLegacySchedulerEligibleAt(any())).willReturn(true);

    // DB-first: autoSettle called with sentinel hash
    Reservation settledWithSentinel = org.mockito.Mockito.mock(Reservation.class);
    given(settledWithSentinel.getId()).willReturn(reservationId);
    given(settledWithSentinel.getStatus()).willReturn(ReservationStatus.AUTO_SETTLED);
    given(settledWithSentinel.getTxHash()).willReturn(EscrowDispatchEventListener.PENDING_TX_HASH);
    given(fresh.autoSettle(EscrowDispatchEventListener.PENDING_TX_HASH))
        .willReturn(settledWithSentinel);
    given(saveReservationPort.save(settledWithSentinel)).willReturn(settledWithSentinel);
    given(loadReservationPort.findByIdWithLock(reservationId))
        .willReturn(Optional.of(fresh))
        .willReturn(Optional.of(settledWithSentinel));

    // Escrow called after DB save
    String realTxHash = "0xhash";
    given(submitEscrowTransactionPort.submitAdminSettle(orderId)).willReturn(realTxHash);

    // txHash write-back
    Reservation settledWithRealTxHash = org.mockito.Mockito.mock(Reservation.class);
    given(settledWithSentinel.updateTxHash(realTxHash)).willReturn(settledWithRealTxHash);
    given(saveReservationPort.save(settledWithRealTxHash)).willReturn(settledWithRealTxHash);
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
    order.verify(saveReservationPort).save(settledWithSentinel);
    order.verify(submitEscrowTransactionPort).submitAdminSettle(orderId);
    order.verify(saveReservationPort).save(settledWithRealTxHash);
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
    given(fresh.getStatus()).willReturn(ReservationStatus.APPROVED);
    given(fresh.isLegacySchedulerEligibleAt(any())).willReturn(true);

    Reservation settledWithSentinel = org.mockito.Mockito.mock(Reservation.class);
    given(settledWithSentinel.getStatus()).willReturn(ReservationStatus.AUTO_SETTLED);
    given(settledWithSentinel.getTxHash()).willReturn(EscrowDispatchEventListener.PENDING_TX_HASH);
    given(fresh.autoSettle(EscrowDispatchEventListener.PENDING_TX_HASH))
        .willReturn(settledWithSentinel);
    given(saveReservationPort.save(settledWithSentinel)).willReturn(settledWithSentinel);
    given(loadReservationPort.findByIdWithLock(reservationId))
        .willReturn(Optional.of(fresh))
        .willReturn(Optional.of(settledWithSentinel));
    given(submitEscrowTransactionPort.submitAdminSettle(orderId)).willReturn("0xhash");
    willThrow(new IllegalStateException("db down"))
        .given(settledWithSentinel)
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

    then(submitEscrowTransactionPort).should().submitAdminSettle(orderId);
    then(saveReservationPort).should(times(1)).save(settledWithSentinel);
  }

  @Test
  @DisplayName("process - APPROVED가 아닌 상태이면 처리를 건너뛴다 (stale-read guard)")
  void process_skipsIfNotApproved() {
    // Arrange: fresh locked row is already SETTLED (concurrent complete)
    Reservation stale = org.mockito.Mockito.mock(Reservation.class);
    Long reservationId = 2L;
    given(stale.getId()).willReturn(reservationId);

    Reservation fresh = org.mockito.Mockito.mock(Reservation.class);
    given(fresh.getId()).willReturn(reservationId);
    given(fresh.getStatus()).willReturn(ReservationStatus.SETTLED);

    given(loadReservationPort.findByIdWithLock(reservationId)).willReturn(Optional.of(fresh));

    // Act
    sut.process(stale);

    // Assert — no DB save, no escrow call
    org.mockito.Mockito.verifyNoInteractions(saveReservationPort, submitEscrowTransactionPort);
  }

  @Test
  @DisplayName("process - USER_EIP7702 locked row이면 legacy auto-settle을 건너뛴다")
  void process_skipsUserEip7702LockedRow() {
    Reservation stale = org.mockito.Mockito.mock(Reservation.class);
    Long reservationId = 3L;
    given(stale.getId()).willReturn(reservationId);

    Reservation fresh = org.mockito.Mockito.mock(Reservation.class);
    given(fresh.getId()).willReturn(reservationId);
    given(fresh.getStatus()).willReturn(ReservationStatus.APPROVED);
    given(fresh.isLegacySchedulerEligibleAt(any())).willReturn(false);

    given(loadReservationPort.findByIdWithLock(reservationId)).willReturn(Optional.of(fresh));

    sut.process(stale);

    org.mockito.Mockito.verifyNoInteractions(saveReservationPort, submitEscrowTransactionPort);
  }

  private AtomicReference<Runnable> captureAfterCommitCallback() {
    AtomicReference<Runnable> callback = new AtomicReference<>();
    willAnswer(
            invocation -> {
              callback.set(invocation.getArgument(1, Runnable.class));
              return null;
            })
        .given(runReservationPostCommitPort)
        .afterCommit(eq("AutoSettle"), any());
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
