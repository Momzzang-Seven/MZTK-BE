package momzzangseven.mztkbe.modules.level.infrastructure.persistence.adapter;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.level.application.port.out.XpLedgerPort;
import momzzangseven.mztkbe.modules.level.domain.model.XpLedgerEntry;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.XpLedgerEntity;
import momzzangseven.mztkbe.modules.level.infrastructure.repository.XpLedgerJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class XpLedgerPersistenceAdapter implements XpLedgerPort {

  private final XpLedgerJpaRepository xpLedgerJpaRepository;
  private final EntityManager entityManager;

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
  public Optional<XpLedgerEntry> findLatestByUserIdAndTypeAndEarnedOn(
      Long userId, XpType type, LocalDate earnedOn) {
    return xpLedgerJpaRepository
        .findTopByUserIdAndTypeAndEarnedOnOrderByCreatedAtDesc(userId, type, earnedOn)
        .map(XpLedgerEntity::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<XpLedgerEntry> loadXpLedgerEntries(Long userId, int page, int size) {
    int fetchSize = Math.max(1, size + 1);
    int offset = Math.max(0, page) * Math.max(1, size);

    List<XpLedgerEntity> entities =
        entityManager
            .createQuery(
                "select x from XpLedgerEntity x where x.userId = :userId order by x.createdAt desc",
                XpLedgerEntity.class)
            .setParameter("userId", userId)
            .setFirstResult(offset)
            .setMaxResults(fetchSize)
            .getResultList();

    return entities.stream().map(XpLedgerEntity::toDomain).toList();
  }

  @Override
  @Transactional
  public boolean trySaveXpLedger(XpLedgerEntry entry) {
    try {
      xpLedgerJpaRepository.saveAndFlush(XpLedgerEntity.from(entry));
      return true;
    } catch (DataIntegrityViolationException e) {
      return false;
    }
  }
}
