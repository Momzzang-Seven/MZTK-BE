package momzzangseven.mztkbe.modules.level.infrastructure.persistence.adapter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.PendingXpGrant;
import momzzangseven.mztkbe.modules.level.application.port.out.XpGrantOutboxPort;
import momzzangseven.mztkbe.modules.level.domain.vo.XpGrantOutboxStatus;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.XpGrantOutboxEntity;
import momzzangseven.mztkbe.modules.level.infrastructure.repository.XpGrantOutboxJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class XpGrantOutboxPersistenceAdapter implements XpGrantOutboxPort {

  private final XpGrantOutboxJpaRepository repository;

  @Override
  @Transactional
  public void enqueue(GrantXpCommand command) {
    try {
      repository.saveAndFlush(XpGrantOutboxEntity.pendingFrom(command, LocalDateTime.now()));
    } catch (DataIntegrityViolationException e) {
      log.info("XP grant already queued (idempotency): key={}", command.idempotencyKey());
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<PendingXpGrant> findDueBatch(LocalDateTime now, int limit) {
    return repository
        .findDueBatch(XpGrantOutboxStatus.PENDING, now, PageRequest.of(0, Math.max(1, limit)))
        .stream()
        .map(XpGrantOutboxEntity::toPending)
        .toList();
  }

  @Override
  @Transactional
  public Optional<PendingXpGrant> claimForProcessing(Long id, LocalDateTime now) {
    return repository.findByIdForUpdateSkipLocked(id, now).map(XpGrantOutboxEntity::toPending);
  }

  @Override
  @Transactional
  public void markDone(Long id) {
    repository.findById(id).ifPresent(XpGrantOutboxEntity::markDone);
  }

  @Override
  @Transactional
  public void recordFailure(Long id, int maxAttempts, int backoffSeconds, String error) {
    // Re-lock the row (blocking FOR UPDATE) and act only while it is still PENDING: process() has
    // already released its lock, so another worker may have driven the row to a terminal DONE in
    // between. A PENDING-guarded locked read returns empty in that case, so a late failure can
    // never overwrite a terminal state.
    repository
        .findPendingByIdForUpdate(id)
        .ifPresent(
            row -> row.recordFailure(maxAttempts, backoffSeconds, error, LocalDateTime.now()));
  }
}
