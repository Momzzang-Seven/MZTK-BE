package momzzangseven.mztkbe.modules.verification.infrastructure.persistence.adapter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.verification.application.port.out.VerificationRequestPort;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;
import momzzangseven.mztkbe.modules.verification.infrastructure.persistence.entity.VerificationRequestEntity;
import momzzangseven.mztkbe.modules.verification.infrastructure.persistence.repository.VerificationRequestJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class VerificationRequestPersistenceAdapter implements VerificationRequestPort {

  private static final EnumSet<VerificationStatus> TODAY_BLOCKING_STATUSES =
      EnumSet.of(
          VerificationStatus.PENDING,
          VerificationStatus.ANALYZING,
          VerificationStatus.RETRY_SCHEDULED,
          VerificationStatus.VERIFIED);

  private final VerificationRequestJpaRepository repository;

  @Override
  @Transactional
  public VerificationRequest save(VerificationRequest verificationRequest) {
    VerificationRequestEntity saved =
        repository.saveAndFlush(VerificationRequestEntity.from(verificationRequest));
    return saved.toDomain();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<VerificationRequest> findTodayActiveOrVerified(Long userId, LocalDate slotDate) {
    LocalDateTime start = slotDate.atStartOfDay();
    LocalDateTime endExclusive = start.plusDays(1);
    return repository
        .findLatestBlockingRequestForSlot(userId, start, endExclusive, TODAY_BLOCKING_STATUSES)
        .map(VerificationRequestEntity::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsSameFingerprint(
      Long userId, VerificationKind verificationKind, String requestFingerprint) {
    return repository.existsByUserIdAndVerificationKindAndRequestFingerprint(
        userId, verificationKind, requestFingerprint);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<VerificationRequest> findLatestByUserId(Long userId) {
    return repository
        .findTopByUserIdOrderByCreatedAtDesc(userId)
        .map(VerificationRequestEntity::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<VerificationRequest> findByVerificationId(String verificationId) {
    return repository.findByVerificationId(verificationId).map(VerificationRequestEntity::toDomain);
  }
}
