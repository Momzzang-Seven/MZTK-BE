package momzzangseven.mztkbe.modules.level.infrastructure.persistence.adapter;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.level.application.port.out.XpLedgerPort;
import momzzangseven.mztkbe.modules.level.domain.model.XpLedgerEntry;
import momzzangseven.mztkbe.modules.level.domain.model.XpType;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.XpLedgerEntity;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.repository.XpLedgerJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class XpLedgerPersistenceAdapter implements XpLedgerPort {

  private final XpLedgerJpaRepository xpLedgerJpaRepository;

  @Override
  @Transactional(readOnly = true)
  public boolean existsByUserIdAndIdempotencyKey(Long userId, String idempotencyKey) {
    return xpLedgerJpaRepository.existsByUserIdAndIdempotencyKey(userId, idempotencyKey);
  }

  @Override
  @Transactional(readOnly = true)
  public int countByUserIdAndTypeAndEarnedOn(Long userId, XpType type, LocalDate earnedOn) {
    return xpLedgerJpaRepository.countByUserIdAndTypeAndEarnedOn(userId, type, earnedOn);
  }

  @Override
  @Transactional(readOnly = true)
  public List<XpLedgerEntry> loadXpLedgerEntries(Long userId, int page, int size) {
    int fetchSize = Math.max(1, size + 1);
    return xpLedgerJpaRepository
        .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, fetchSize))
        .getContent()
        .stream()
        .map(this::mapToDomain)
        .toList();
  }

  @Override
  @Transactional
  public boolean trySaveXpLedger(XpLedgerEntry entry) {
    try {
      xpLedgerJpaRepository.saveAndFlush(mapToEntity(entry));
      return true;
    } catch (DataIntegrityViolationException e) {
      return false;
    }
  }

  private XpLedgerEntry mapToDomain(XpLedgerEntity entity) {
    return XpLedgerEntry.builder()
        .id(entity.getId())
        .userId(entity.getUserId())
        .type(entity.getType())
        .xpAmount(entity.getXpAmount())
        .earnedOn(entity.getEarnedOn())
        .occurredAt(entity.getOccurredAt())
        .idempotencyKey(entity.getIdempotencyKey())
        .sourceRef(entity.getSourceRef())
        .createdAt(entity.getCreatedAt())
        .build();
  }

  private XpLedgerEntity mapToEntity(XpLedgerEntry entry) {
    return XpLedgerEntity.builder()
        .id(entry.getId())
        .userId(entry.getUserId())
        .type(entry.getType())
        .xpAmount(entry.getXpAmount())
        .earnedOn(entry.getEarnedOn())
        .occurredAt(entry.getOccurredAt())
        .idempotencyKey(entry.getIdempotencyKey())
        .sourceRef(entry.getSourceRef())
        .createdAt(entry.getCreatedAt())
        .build();
  }
}
