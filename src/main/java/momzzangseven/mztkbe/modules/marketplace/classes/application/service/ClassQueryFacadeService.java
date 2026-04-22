package momzzangseven.mztkbe.modules.marketplace.classes.application.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.GetClassInfoUseCase;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.GetClassSlotInfoUseCase;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassSlotPort;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.ClassSlot;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.MarketplaceClass;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exposes class and slot read operations to other modules via input ports.
 *
 * <p>This service exists solely as the cross-module API surface for the {@code classes} submodule.
 * Callers in other submodules (e.g., {@code reservation}) must depend on the use case interfaces
 * ({@link GetClassInfoUseCase}, {@link GetClassSlotInfoUseCase}), never on the output ports
 * ({@code LoadClassPort}, {@code LoadClassSlotPort}) directly.
 */
@Service
@RequiredArgsConstructor
public class ClassQueryFacadeService implements GetClassInfoUseCase, GetClassSlotInfoUseCase {

  private final LoadClassPort loadClassPort;
  private final LoadClassSlotPort loadClassSlotPort;

  @Override
  @Transactional(readOnly = true)
  public Optional<MarketplaceClass> findById(Long classId) {
    return loadClassPort.findById(classId);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ClassSlot> findByIdWithLock(Long slotId) {
    return loadClassSlotPort.findByIdWithLock(slotId);
  }
}
