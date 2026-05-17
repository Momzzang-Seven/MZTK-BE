package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceWeb3DisabledException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowOrderView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowOrderPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationChainReadRepairService 단위 테스트")
class ReservationChainReadRepairServiceTest {

  private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2025-06-01T03:00:00Z"), ZONE);

  @Mock private LoadReservationPort loadReservationPort;
  @Mock private LoadReservationEscrowOrderPort loadReservationEscrowOrderPort;
  @Mock private SaveReservationPort saveReservationPort;

  @Test
  @DisplayName("batch repair는 여러 sync-required 예약을 getOrders 한 번으로 조회하고 CREATED 상태를 LOCKED로 저장한다")
  void repairBatch_usesGetOrdersOnceAndSyncsCreatedOrder() {
    Reservation first = syncRequiredReservation(1L, "0x" + "0".repeat(63) + "1");
    Reservation second = syncRequiredReservation(2L, "0x" + "0".repeat(63) + "2");
    long deadline = Instant.parse("2025-06-10T03:00:00Z").getEpochSecond();
    given(
            loadReservationEscrowOrderPort.getOrders(
                List.of(first.getOrderKey(), second.getOrderKey())))
        .willReturn(
            List.of(
                createdOrder(first.getOrderKey(), deadline),
                confirmedOrder(second.getOrderKey(), deadline)));
    given(saveReservationPort.save(any()))
        .willAnswer(invocation -> invocation.getArgument(0, Reservation.class));
    given(loadReservationPort.findByIdWithLock(first.getId())).willReturn(Optional.of(first));
    given(loadReservationPort.findByIdWithLock(second.getId())).willReturn(Optional.of(second));
    ReservationChainReadRepairService sut =
        new ReservationChainReadRepairService(
            loadReservationPort, loadReservationEscrowOrderPort, saveReservationPort, CLOCK);

    List<Reservation> repaired = sut.repairBatch(List.of(first, second));

    assertThat(repaired)
        .extracting(Reservation::getStatus)
        .containsExactly(ReservationStatus.PENDING, ReservationStatus.SETTLED);
    then(loadReservationEscrowOrderPort)
        .should()
        .getOrders(List.of(first.getOrderKey(), second.getOrderKey()));
    then(loadReservationEscrowOrderPort).shouldHaveNoMoreInteractions();
    ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
    then(saveReservationPort).should(org.mockito.Mockito.times(2)).save(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(Reservation::getStatus)
        .containsExactly(ReservationStatus.PENDING, ReservationStatus.SETTLED);
  }

  @Test
  @DisplayName("CREATED 주문의 deadline이 완료 가능 window보다 짧으면 DEADLINE_RECOVERY_REQUIRED로 저장한다")
  void repairBatch_createdOrderUnsafeDeadline_marksRecoveryRequired() {
    Reservation reservation = syncRequiredReservation(1L, "0x" + "0".repeat(63) + "1");
    long unsafeDeadline = Instant.parse("2025-06-01T04:00:00Z").getEpochSecond();
    given(loadReservationEscrowOrderPort.getOrders(List.of(reservation.getOrderKey())))
        .willReturn(List.of(createdOrder(reservation.getOrderKey(), unsafeDeadline)));
    given(saveReservationPort.save(any()))
        .willAnswer(invocation -> invocation.getArgument(0, Reservation.class));
    given(loadReservationPort.findByIdWithLock(reservation.getId()))
        .willReturn(Optional.of(reservation));
    ReservationChainReadRepairService sut =
        new ReservationChainReadRepairService(
            loadReservationPort, loadReservationEscrowOrderPort, saveReservationPort, CLOCK);

    List<Reservation> repaired = sut.repairBatch(List.of(reservation));

    assertThat(repaired.getFirst().getStatus())
        .isEqualTo(ReservationStatus.DEADLINE_RECOVERY_REQUIRED);
  }

  @Test
  @DisplayName("batch repair는 CANCELLED와 DEADLINE_REFUNDED 주문을 local terminal 상태로 저장한다")
  void repairBatch_syncsCancelledAndDeadlineRefundedOrders() {
    Reservation cancelled = syncRequiredReservation(1L, "0x" + "0".repeat(63) + "1");
    Reservation refunded = syncRequiredReservation(2L, "0x" + "0".repeat(63) + "2");
    long deadline = Instant.parse("2025-06-10T03:00:00Z").getEpochSecond();
    given(
            loadReservationEscrowOrderPort.getOrders(
                List.of(cancelled.getOrderKey(), refunded.getOrderKey())))
        .willReturn(
            List.of(
                order(
                    cancelled.getOrderKey(), deadline, ReservationEscrowOrderView.STATE_CANCELLED),
                order(
                    refunded.getOrderKey(),
                    deadline,
                    ReservationEscrowOrderView.STATE_DEADLINE_REFUNDED)));
    given(saveReservationPort.save(any()))
        .willAnswer(invocation -> invocation.getArgument(0, Reservation.class));
    given(loadReservationPort.findByIdWithLock(cancelled.getId()))
        .willReturn(Optional.of(cancelled));
    given(loadReservationPort.findByIdWithLock(refunded.getId())).willReturn(Optional.of(refunded));
    ReservationChainReadRepairService sut =
        new ReservationChainReadRepairService(
            loadReservationPort, loadReservationEscrowOrderPort, saveReservationPort, CLOCK);

    List<Reservation> repaired = sut.repairBatch(List.of(cancelled, refunded));

    assertThat(repaired)
        .extracting(Reservation::getStatus)
        .containsExactly(ReservationStatus.USER_CANCELLED, ReservationStatus.DEADLINE_REFUNDED);
  }

  @Test
  @DisplayName("Web3 disabled 상태에서는 read API를 깨지 않고 원본 예약을 반환한다")
  void repairBatch_web3Disabled_returnsOriginalReservations() {
    Reservation reservation = syncRequiredReservation(1L, "0x" + "0".repeat(63) + "1");
    given(loadReservationEscrowOrderPort.getOrders(List.of(reservation.getOrderKey())))
        .willThrow(new MarketplaceWeb3DisabledException());
    ReservationChainReadRepairService sut =
        new ReservationChainReadRepairService(
            loadReservationPort, loadReservationEscrowOrderPort, saveReservationPort, CLOCK);

    List<Reservation> repaired = sut.repairBatch(List.of(reservation));

    assertThat(repaired).containsExactly(reservation);
    then(saveReservationPort).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("repairOne은 단건 상세 조회에서 getOrder를 사용한다")
  void repairOne_usesGetOrder() {
    Reservation reservation = syncRequiredReservation(1L, "0x" + "0".repeat(63) + "1");
    long deadline = Instant.parse("2025-06-10T03:00:00Z").getEpochSecond();
    given(loadReservationEscrowOrderPort.getOrder(reservation.getOrderKey()))
        .willReturn(createdOrder(reservation.getOrderKey(), deadline));
    given(saveReservationPort.save(any()))
        .willAnswer(invocation -> invocation.getArgument(0, Reservation.class));
    given(loadReservationPort.findByIdWithLock(reservation.getId()))
        .willReturn(Optional.of(reservation));
    ReservationChainReadRepairService sut =
        new ReservationChainReadRepairService(
            loadReservationPort, loadReservationEscrowOrderPort, saveReservationPort, CLOCK);

    Reservation repaired = sut.repairOne(reservation);

    assertThat(repaired.getStatus()).isEqualTo(ReservationStatus.PENDING);
    then(loadReservationEscrowOrderPort).should().getOrder(reservation.getOrderKey());
  }

  private Reservation syncRequiredReservation(Long id, String orderKey) {
    return Reservation.builder()
        .id(id)
        .userId(10L)
        .trainerId(20L)
        .slotId(30L)
        .reservationDate(LocalDate.of(2025, 6, 1))
        .reservationTime(LocalTime.of(10, 0))
        .durationMinutes(60)
        .status(ReservationStatus.DEADLINE_SYNC_REQUIRED)
        .orderId("123e4567-e89b-12d3-a456-426614174000")
        .orderKey(orderKey)
        .bookedPriceAmount(50_000)
        .version(0L)
        .build();
  }

  private ReservationEscrowOrderView createdOrder(String orderKey, long deadlineEpochSeconds) {
    return new ReservationEscrowOrderView(
        orderKey,
        "50000",
        "0x3333333333333333333333333333333333333333",
        deadlineEpochSeconds,
        ReservationEscrowOrderView.STATE_CREATED,
        "0x1111111111111111111111111111111111111111",
        "0x2222222222222222222222222222222222222222");
  }

  private ReservationEscrowOrderView confirmedOrder(String orderKey, long deadlineEpochSeconds) {
    return order(orderKey, deadlineEpochSeconds, ReservationEscrowOrderView.STATE_CONFIRMED);
  }

  private ReservationEscrowOrderView order(String orderKey, long deadlineEpochSeconds, int state) {
    return new ReservationEscrowOrderView(
        orderKey,
        "50000",
        "0x3333333333333333333333333333333333333333",
        deadlineEpochSeconds,
        state,
        "0x1111111111111111111111111111111111111111",
        "0x2222222222222222222222222222222222222222");
  }
}
