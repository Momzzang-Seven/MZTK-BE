package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.classes;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.ClassReservationProjection;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.ClassSlotReservationProjection;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.GetClassInfoUseCase;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.GetClassSlotInfoUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationClassPort;
import org.springframework.stereotype.Component;

/**
 * Cross-module adapter that isolates reservation application services from classes module types.
 */
@Component
@RequiredArgsConstructor
public class ReservationClassAdapter implements LoadReservationClassPort {

  private final GetClassSlotInfoUseCase getClassSlotInfoUseCase;
  private final GetClassInfoUseCase getClassInfoUseCase;

  @Override
  public Optional<ReservationClassSlotView> findSlotByIdWithLock(Long slotId) {
    return getClassSlotInfoUseCase
        .findReservationProjectionByIdWithLock(slotId)
        .map(this::toSlotView);
  }

  @Override
  public Optional<ReservationClassView> findClassById(Long classId) {
    return getClassInfoUseCase.findReservationProjectionById(classId).map(this::toClassView);
  }

  private ReservationClassSlotView toSlotView(ClassSlotReservationProjection slot) {
    return new ReservationClassSlotView(
        slot.slotId(),
        slot.classId(),
        slot.daysOfWeek(),
        slot.startTime(),
        slot.capacity(),
        slot.active());
  }

  private ReservationClassView toClassView(ClassReservationProjection cls) {
    return new ReservationClassView(
        cls.classId(),
        cls.trainerId(),
        cls.priceAmount(),
        cls.durationMinutes(),
        cls.title(),
        cls.active());
  }
}
