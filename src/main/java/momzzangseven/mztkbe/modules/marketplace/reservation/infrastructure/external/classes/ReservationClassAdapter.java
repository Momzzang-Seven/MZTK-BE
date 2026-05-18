package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.classes;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.GetClassInfoUseCase;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.GetClassSlotInfoUseCase;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.ClassSlot;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.MarketplaceClass;
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
    return getClassSlotInfoUseCase.findByIdWithLock(slotId).map(this::toSlotView);
  }

  @Override
  public Optional<ReservationClassView> findClassById(Long classId) {
    return getClassInfoUseCase.findById(classId).map(this::toClassView);
  }

  private ReservationClassSlotView toSlotView(ClassSlot slot) {
    return new ReservationClassSlotView(
        slot.getId(),
        slot.getClassId(),
        slot.getDaysOfWeek(),
        slot.getStartTime(),
        slot.getCapacity(),
        slot.isActive());
  }

  private ReservationClassView toClassView(MarketplaceClass cls) {
    return new ReservationClassView(
        cls.getId(),
        cls.getTrainerId(),
        cls.getPriceAmount(),
        cls.getDurationMinutes(),
        cls.getTitle(),
        cls.isActive());
  }
}
