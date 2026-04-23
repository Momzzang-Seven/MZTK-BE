package momzzangseven.mztkbe.modules.marketplace.classes.application.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.marketplace.ClassNotFoundException;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetClassReservationInfoQuery;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetClassReservationInfoResult;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetClassReservationInfoResult.AvailableDateInfo;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetClassReservationInfoResult.AvailableTimeInfo;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.GetClassReservationInfoUseCase;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassSlotPort;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.ClassSlot;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.MarketplaceClass;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetSlotReservationInfoUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service that builds a 4-week (28-day) availability calendar for a class.
 *
 * <p>For each active slot, computes which dates in the next 28 days fall on matching days of the
 * week, then subtracts the count of active reservations to derive {@code availableCapacity}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetClassReservationInfoService implements GetClassReservationInfoUseCase {

  private static final int DAYS_WINDOW = 28;

  private final LoadClassPort loadClassPort;
  private final LoadClassSlotPort loadClassSlotPort;
  private final GetSlotReservationInfoUseCase getSlotReservationInfoUseCase;

  /**
   * Clock for computing today's date. Injected for testability — unit tests can provide a fixed
   * clock to assert calendar output deterministically.
   *
   * <p>Bound to {@code Asia/Seoul} in production via the {@code @Bean Clock} in application config.
   */
  private final Clock clock;

  @Override
  @Transactional(readOnly = true)
  public GetClassReservationInfoResult execute(GetClassReservationInfoQuery query) {
    log.debug("GetClassReservationInfo: classId={}", query.classId());

    MarketplaceClass cls =
        loadClassPort
            .findById(query.classId())
            .orElseThrow(() -> new ClassNotFoundException(query.classId()));

    List<ClassSlot> activeSlots =
        loadClassSlotPort.findByClassId(query.classId()).stream()
            .filter(ClassSlot::isActive)
            .toList();

    if (activeSlots.isEmpty()) {
      return buildResult(cls, List.of());
    }

    LocalDate today = LocalDate.now(clock);
    LocalDate windowEnd = today.plusDays(DAYS_WINDOW);

    // Map: slotId -> (Map: date -> count)
    Map<Long, Map<LocalDate, Integer>> activeCountBySlotAndDate = new java.util.HashMap<>();
    for (ClassSlot slot : activeSlots) {
      activeCountBySlotAndDate.put(
          slot.getId(),
          getSlotReservationInfoUseCase.countActiveReservationsForDateRange(
              slot.getId(), today, windowEnd));
    }

    List<AvailableDateInfo> availableDates = new ArrayList<>();
    for (LocalDate date = today; date.isBefore(windowEnd); date = date.plusDays(1)) {
      final LocalDate currentDate = date;
      List<AvailableTimeInfo> timesForDate = new ArrayList<>();

      for (ClassSlot slot : activeSlots) {
        if (slot.getDaysOfWeek().contains(currentDate.getDayOfWeek())) {
          // If it's today, filter out slots that have already started
          if (currentDate.equals(today) && slot.getStartTime().isBefore(java.time.LocalTime.now(clock))) {
            continue;
          }
          
          int active = activeCountBySlotAndDate.get(slot.getId()).getOrDefault(currentDate, 0);
          int available = Math.max(0, slot.getCapacity() - active);
          timesForDate.add(
              new AvailableTimeInfo(
                  slot.getId(), slot.getStartTime(), slot.getCapacity(), available));
        }
      }

      if (!timesForDate.isEmpty()) {
        availableDates.add(new AvailableDateInfo(currentDate, timesForDate));
      }
    }

    return buildResult(cls, availableDates);
  }

  private GetClassReservationInfoResult buildResult(
      MarketplaceClass cls, List<AvailableDateInfo> availableDates) {
    return new GetClassReservationInfoResult(
        cls.getId(),
        cls.getTitle(),
        cls.getTrainerId(),
        cls.getPriceAmount(),
        cls.getDurationMinutes(),
        availableDates);
  }
}
