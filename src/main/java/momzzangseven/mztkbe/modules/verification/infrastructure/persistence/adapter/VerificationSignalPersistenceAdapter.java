package momzzangseven.mztkbe.modules.verification.infrastructure.persistence.adapter;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.verification.application.port.out.VerificationSignalPort;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationSignal;
import momzzangseven.mztkbe.modules.verification.infrastructure.persistence.entity.VerificationSignalEntity;
import momzzangseven.mztkbe.modules.verification.infrastructure.persistence.repository.VerificationSignalJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class VerificationSignalPersistenceAdapter implements VerificationSignalPort {

  private final VerificationSignalJpaRepository repository;

  @Override
  @Transactional
  public VerificationSignal save(VerificationSignal signal) {
    VerificationSignalEntity saved = repository.saveAndFlush(VerificationSignalEntity.from(signal));
    return saved.toDomain();
  }

  @Override
  @Transactional(readOnly = true)
  public List<VerificationSignal> findByVerificationRequestId(Long verificationRequestId) {
    return repository.findByVerificationRequestIdOrderByCreatedAtAsc(verificationRequestId).stream()
        .map(VerificationSignalEntity::toDomain)
        .toList();
  }
}
