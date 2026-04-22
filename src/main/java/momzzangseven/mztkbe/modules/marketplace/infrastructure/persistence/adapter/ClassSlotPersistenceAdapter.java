package momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadClassSlotPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.SaveClassSlotPort;
import momzzangseven.mztkbe.modules.marketplace.domain.model.ClassSlot;
import momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.entity.ClassSlotEntity;
import momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.repository.ClassSlotJpaRepository;
import org.springframework.stereotype.Component;

/**
 * Persistence adapter implementing {@link LoadClassSlotPort} and {@link SaveClassSlotPort}.
 *
 * <p>Thin JPA pass-through following the project convention. Domain ↔ Entity conversion is the only
 * responsibility.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClassSlotPersistenceAdapter implements LoadClassSlotPort, SaveClassSlotPort {

  private final ClassSlotJpaRepository classSlotJpaRepository;

  // ========== LoadClassSlotPort ==========

  @Override
  public List<ClassSlot> findByClassId(Long classId) {
    log.debug("Loading slots for classId={}", classId);
    return classSlotJpaRepository.findByClassId(classId).stream()
        .map(ClassSlotEntity::toDomain)
        .toList();
  }

  @Override
  public List<ClassSlot> findByClassIdWithLock(Long classId) {
    log.debug("Loading slots with pessimistic lock for classId={}", classId);
    return classSlotJpaRepository.findByClassIdWithLock(classId).stream()
        .map(ClassSlotEntity::toDomain)
        .toList();
  }

  @Override
  public Optional<ClassSlot> findByIdWithLock(Long slotId) {
    log.debug("Loading slot with pessimistic lock for slotId={}", slotId);
    return classSlotJpaRepository.findByIdWithLock(slotId).map(ClassSlotEntity::toDomain);
  }

  // ========== SaveClassSlotPort ==========

  @Override
  public List<ClassSlot> saveAll(List<ClassSlot> slots) {
    log.debug("Saving {} slots", slots.size());
    List<ClassSlotEntity> entities = slots.stream().map(ClassSlotEntity::fromDomain).toList();
    return classSlotJpaRepository.saveAll(entities).stream()
        .map(ClassSlotEntity::toDomain)
        .toList();
  }

  @Override
  public void deleteById(Long slotId) {
    log.debug("Hard-deleting slot id={}", slotId);
    classSlotJpaRepository.deleteById(slotId);
  }
}
