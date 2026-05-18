package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceWeb3DisabledException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowOrderView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RepairReservationChainReadUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowOrderPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class ReservationChainReadRepairService implements RepairReservationChainReadUseCase {

  private final LoadReservationPort loadReservationPort;
  private final LoadReservationEscrowOrderPort loadReservationEscrowOrderPort;
  private final SaveReservationPort saveReservationPort;
  private final Clock clock;
  private TransactionOperations transactionOperations;

  public ReservationChainReadRepairService(
      LoadReservationPort loadReservationPort,
      @Nullable LoadReservationEscrowOrderPort loadReservationEscrowOrderPort,
      SaveReservationPort saveReservationPort,
      Clock clock) {
    this.loadReservationPort = loadReservationPort;
    this.loadReservationEscrowOrderPort =
        loadReservationEscrowOrderPort == null
            ? DisabledReservationWeb3PortFactory.escrowOrder()
            : loadReservationEscrowOrderPort;
    this.saveReservationPort = saveReservationPort;
    this.clock = clock;
  }

  @Autowired
  void setTransactionManager(PlatformTransactionManager transactionManager) {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.transactionOperations = transactionTemplate;
  }

  @Override
  public Reservation repairOne(Reservation reservation) {
    if (!needsChainRepair(reservation)) {
      return reservation;
    }
    try {
      ReservationEscrowOrderView order =
          loadReservationEscrowOrderPort.getOrder(reservation.getOrderKey());
      return repairFromOrder(reservation, order);
    } catch (MarketplaceWeb3DisabledException e) {
      return reservation;
    } catch (RuntimeException e) {
      log.warn(
          "Skipping reservation chain read repair after getOrder failure: reservationId={},"
              + " orderKey={}",
          reservation.getId(),
          reservation.getOrderKey(),
          e);
      return reservation;
    }
  }

  @Override
  public List<Reservation> repairBatch(List<Reservation> reservations) {
    List<Reservation> candidates = reservations.stream().filter(this::needsChainRepair).toList();
    if (candidates.isEmpty()) {
      return reservations;
    }
    Map<String, ReservationEscrowOrderView> ordersByKey;
    try {
      ordersByKey =
          loadReservationEscrowOrderPort.getOrders(orderKeys(candidates)).stream()
              .collect(
                  Collectors.toMap(
                      order -> normalize(order.orderKey()),
                      Function.identity(),
                      (left, ignored) -> left,
                      LinkedHashMap::new));
    } catch (MarketplaceWeb3DisabledException e) {
      return reservations;
    } catch (RuntimeException e) {
      log.warn(
          "Skipping reservation chain read repair batch after getOrders failure: reservationIds={}",
          candidates.stream().map(Reservation::getId).toList(),
          e);
      return reservations;
    }
    return reservations.stream()
        .map(
            reservation ->
                needsChainRepair(reservation)
                    ? repairFromOrder(
                        reservation, ordersByKey.get(normalize(reservation.getOrderKey())))
                    : reservation)
        .toList();
  }

  private Collection<String> orderKeys(List<Reservation> reservations) {
    return reservations.stream()
        .map(Reservation::getOrderKey)
        .filter(orderKey -> orderKey != null && !orderKey.isBlank())
        .map(this::normalize)
        .distinct()
        .toList();
  }

  private boolean needsChainRepair(Reservation reservation) {
    return reservation != null
        && reservation.getOrderKey() != null
        && !reservation.getOrderKey().isBlank()
        && (reservation.getStatus() == ReservationStatus.DEADLINE_SYNC_REQUIRED
            || reservation.getStatus() == ReservationStatus.DEADLINE_RECOVERY_REQUIRED);
  }

  private Reservation repairFromOrder(Reservation reservation, ReservationEscrowOrderView order) {
    if (order == null || order.isAbsent()) {
      return reservation;
    }
    return runInTransaction(
        () -> {
          Reservation current =
              loadReservationPort.findByIdWithLock(reservation.getId()).orElse(reservation);
          if (!needsChainRepair(current)) {
            return current;
          }
          return repairLockedFromOrder(current, order);
        });
  }

  private Reservation repairLockedFromOrder(
      Reservation reservation, ReservationEscrowOrderView order) {
    LocalDateTime deadlineAt = deadlineAt(order.deadlineEpochSeconds());
    Reservation repaired =
        switch (order.state()) {
          case ReservationEscrowOrderView.STATE_CREATED ->
              repairCreatedOrder(reservation, order, deadlineAt);
          case ReservationEscrowOrderView.STATE_CONFIRMED ->
              reservation.syncChainOutcome(
                  ReservationStatus.SETTLED,
                  ReservationEscrowStatus.SETTLED,
                  reservation.getTxHash(),
                  order.deadlineEpochSeconds(),
                  deadlineAt);
          case ReservationEscrowOrderView.STATE_CANCELLED ->
              syncCancelledChainOutcome(reservation, order, deadlineAt);
          case ReservationEscrowOrderView.STATE_ADMIN_SETTLED ->
              reservation.syncChainOutcome(
                  ReservationStatus.AUTO_SETTLED,
                  ReservationEscrowStatus.SETTLED,
                  reservation.getTxHash(),
                  order.deadlineEpochSeconds(),
                  deadlineAt);
          case ReservationEscrowOrderView.STATE_ADMIN_REFUNDED ->
              reservation.syncChainOutcome(
                  ReservationStatus.TIMEOUT_CANCELLED,
                  ReservationEscrowStatus.REFUNDED,
                  reservation.getTxHash(),
                  order.deadlineEpochSeconds(),
                  deadlineAt);
          case ReservationEscrowOrderView.STATE_DEADLINE_REFUNDED ->
              reservation.syncChainOutcome(
                  ReservationStatus.DEADLINE_REFUNDED,
                  ReservationEscrowStatus.DEADLINE_REFUNDED,
                  reservation.getTxHash(),
                  order.deadlineEpochSeconds(),
                  deadlineAt);
          default -> reservation;
        };
    if (repaired == reservation || sameRepairState(reservation, repaired)) {
      return reservation;
    }
    log.info(
        "Reservation chain read repair applied: reservationId={}, fromStatus={}, toStatus={},"
            + " orderState={}",
        reservation.getId(),
        reservation.getStatus(),
        repaired.getStatus(),
        order.state());
    return saveReservationPort.save(repaired);
  }

  private <T> T runInTransaction(java.util.function.Supplier<T> supplier) {
    if (transactionOperations == null) {
      return supplier.get();
    }
    return transactionOperations.execute(status -> supplier.get());
  }

  private Reservation repairCreatedOrder(
      Reservation reservation, ReservationEscrowOrderView order, LocalDateTime deadlineAt) {
    boolean completionWindowFits =
        deadlineAt == null || !reservation.sessionEndAt().plusHours(24).isAfter(deadlineAt);
    return reservation.repairCreatedChainOrder(
        order.deadlineEpochSeconds(), deadlineAt, completionWindowFits);
  }

  private Reservation syncCancelledChainOutcome(
      Reservation reservation, ReservationEscrowOrderView order, LocalDateTime deadlineAt) {
    return reservation.syncChainOutcome(
        ReservationStatus.MANUAL_SYNC_REQUIRED,
        ReservationEscrowStatus.MANUAL_SYNC_REQUIRED,
        reservation.getTxHash(),
        order.deadlineEpochSeconds(),
        deadlineAt);
  }

  private boolean sameRepairState(Reservation before, Reservation after) {
    return before.getStatus() == after.getStatus()
        && before.getEffectiveEscrowStatus() == after.getEffectiveEscrowStatus()
        && equalsNullable(
            before.getContractDeadlineEpochSeconds(), after.getContractDeadlineEpochSeconds())
        && equalsNullable(before.getContractDeadlineAt(), after.getContractDeadlineAt());
  }

  private boolean equalsNullable(Object left, Object right) {
    return left == null ? right == null : left.equals(right);
  }

  private LocalDateTime deadlineAt(Long epochSeconds) {
    if (epochSeconds == null) {
      return null;
    }
    return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), clock.getZone());
  }

  private String normalize(String orderKey) {
    return orderKey == null ? null : orderKey.toLowerCase(Locale.ROOT);
  }
}
